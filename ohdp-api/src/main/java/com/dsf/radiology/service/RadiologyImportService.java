package com.dsf.radiology.service;

import com.dsf.common.exception.BusinessException;
import com.dsf.radiology.dto.RadiologyDto;
import com.dsf.radiology.entity.RadiologyCase;
import com.dsf.radiology.model.RadiologyModality;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.DataValidationConstraint;
import org.apache.poi.ss.usermodel.DataValidationHelper;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class RadiologyImportService {

    public static final String TEMPLATE_VERSION = "2026.2";

    private final RadiologyCaseService caseService;
    private final RadiologyCatalogService catalogService;
    private final DataFormatter dataFormatter = new DataFormatter(Locale.CHINA);

    @Transactional
    public RadiologyDto.ImportPreview importWorkbook(
            Long caseId, MultipartFile file, String importType, String requestedSheet, boolean dryRun) {
        RadiologyCase entity = caseService.requireCase(caseId);
        caseService.assertEditable(entity);
        if (file == null || file.isEmpty()) throw new BusinessException("请选择 Excel 文件");
        String fileName = file.getOriginalFilename() == null ? "import.xlsx" : file.getOriginalFilename();
        if (!fileName.toLowerCase(Locale.ROOT).matches(".*\\.xlsx?$")) throw new BusinessException("仅支持 .xlsx 或 .xls 文件");
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = selectSheet(workbook, requestedSheet, importType);
            if (sheet.getLastRowNum() < 1) throw new BusinessException("导入表至少需要两行表头");
            Row headers = sheet.getRow(1);
            if (headers == null) throw new BusinessException("导入表第 2 行字段代码为空");
            List<Row> dataRows = findDataRows(sheet);
            if (dataRows.isEmpty()) throw new BusinessException("导入表没有数据，请在第 3 行填写一行检测数据");
            if (dataRows.size() > 1) {
                String rows = dataRows.stream().map(row -> String.valueOf(row.getRowNum() + 1))
                        .collect(java.util.stream.Collectors.joining("、"));
                throw new BusinessException("当前项目导入仅允许一行数据，检测到多行：" + rows + "。请每个项目使用一个 Excel 文件导入");
            }
            Row values = dataRows.get(0);
            List<String> ignored = new ArrayList<>();
            List<RadiologyDto.ImportCellError> errors = new ArrayList<>();
            List<RadiologyDto.MeasurementRequest> measurementValues = new ArrayList<>();
            Map<Integer, List<BigDecimal>> pointReadings = new LinkedHashMap<>();
            Map<Integer, Integer> pointFirstColumns = new HashMap<>();
            Map<String, Set<String>> suppliedFields = new LinkedHashMap<>();
            Set<String> duplicateHeaders = new HashSet<>();
            Set<String> seenHeaders = new HashSet<>();
            int recognized = 0;

            for (int column = 0; column < headers.getLastCellNum(); column++) {
                String header = text(headers.getCell(column)).trim();
                if (!StringUtils.hasText(header)) continue;
                String normalizedHeader = header.toUpperCase(Locale.ROOT);
                if (!seenHeaders.add(normalizedHeader)) duplicateHeaders.add(normalizedHeader);
                Cell valueCell = values.getCell(column);
                String rawValue = text(valueCell).trim();
                if (valueCell != null && valueCell.getCellType() == CellType.FORMULA) {
                    errors.add(new RadiologyDto.ImportCellError(
                            column + 1, header, rawValue, "第 " + (values.getRowNum() + 1) + " 行不得使用公式，请粘贴原始数值"));
                    continue;
                }
                if (header.matches("(?i)RPD[1-9]\\d*-\\d+")) {
                    recognized++;
                    if (StringUtils.hasText(rawValue)) {
                        try {
                            int point = Integer.parseInt(header.substring(3, header.indexOf('-')));
                            pointFirstColumns.putIfAbsent(point, column + 1);
                            pointReadings.computeIfAbsent(point, ignoredPoint -> new ArrayList<>()).add(new BigDecimal(rawValue));
                        } catch (NumberFormatException ex) {
                            errors.add(new RadiologyDto.ImportCellError(column + 1, header, rawValue, "应为数值"));
                        }
                    }
                    continue;
                }
                var itemCode = catalogService.itemCodeForImportField(entity.getModality(), header);
                if (itemCode.isEmpty()) {
                    ignored.add(header);
                    continue;
                }
                recognized++;
                if (!StringUtils.hasText(rawValue)) continue;
                String canonicalFieldCode = canonicalFieldCode(itemCode.get(), header);
                suppliedFields.computeIfAbsent(itemCode.get(), ignoredItem -> new HashSet<>())
                        .add(canonicalFieldCode.toUpperCase(Locale.ROOT));
                RadiologyDto.MeasurementRequest request = new RadiologyDto.MeasurementRequest();
                request.setItemCode(itemCode.get());
                request.setFieldCode(canonicalFieldCode);
                try {
                    request.setValueNum(new BigDecimal(rawValue));
                } catch (NumberFormatException ex) {
                    if (itemCode.get().equals("ILA") || itemCode.get().equals("ART") || canonicalFieldCode.equals("STP-MODEL")) {
                        request.setValueText(rawValue);
                    } else {
                        errors.add(new RadiologyDto.ImportCellError(column + 1, header, rawValue, "应为数值"));
                        continue;
                    }
                }
                measurementValues.add(request);
            }

            if (!duplicateHeaders.isEmpty()) {
                errors.add(new RadiologyDto.ImportCellError(1, String.join("、", duplicateHeaders), "", "字段代码重复"));
            }
            validateRequiredGroups(entity, suppliedFields, measurementValues, errors);
            for(String item:suppliedFields.keySet()) if(caseService.itemConditions(entity).containsKey("__rows."+item))
                errors.add(new RadiologyDto.ImportCellError(1,item,"","此项目已采用分行录入，请在测量行编辑；普通单行模板不覆盖分行数据"));
            for (Map.Entry<Integer, List<BigDecimal>> entry : pointReadings.entrySet()) {
                if (entry.getValue().size() != 5) {
                    errors.add(new RadiologyDto.ImportCellError(
                            pointFirstColumns.getOrDefault(entry.getKey(), 1),
                            "RPD" + entry.getKey(),
                            String.valueOf(entry.getValue().size()),
                            "每个防护检测点必须填写完整的 5 次读数"));
                }
            }
            if (recognized == 0) throw new BusinessException("导入表没有可识别的字段代码，请重新下载系统模板");

            int applied = measurementValues.size() + pointReadings.values().stream().mapToInt(List::size).sum();
            if (!dryRun && errors.isEmpty()) {
                if (!measurementValues.isEmpty()) {
                    RadiologyDto.MeasurementBatchRequest batch = new RadiologyDto.MeasurementBatchRequest();
                    batch.setValues(measurementValues);
                    caseService.saveMeasurements(caseId, batch);
                }
                if (!pointReadings.isEmpty()) {
                    RadiologyDto.ProtectionPointBatchRequest batch = new RadiologyDto.ProtectionPointBatchRequest();
                    List<RadiologyDto.ProtectionPointRequest> points = new ArrayList<>();
                    for (Map.Entry<Integer, List<BigDecimal>> entry : pointReadings.entrySet()) {
                        RadiologyDto.ProtectionPointRequest point = new RadiologyDto.ProtectionPointRequest();
                        point.setPointCode("RPD" + entry.getKey());
                        point.setPointName("检测点位" + entry.getKey());
                        point.setReadings(entry.getValue());
                        point.setSortNo(entry.getKey());
                        points.add(point);
                    }
                    batch.setPoints(points);
                    caseService.saveProtectionPoints(caseId, batch);
                }
            }
            return new RadiologyDto.ImportPreview(
                    fileName, sheet.getSheetName(), dryRun, recognized, dryRun ? 0 : (errors.isEmpty() ? applied : 0), ignored, errors);
        } catch (IOException ex) {
            throw new BusinessException("Excel 读取失败：" + ex.getMessage());
        }
    }

    public TemplateFile createTemplate(RadiologyModality modality, String type) {
        boolean protection = "PROTECTION".equalsIgnoreCase(type);
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            createInstructions(workbook, modality, protection);
            Sheet sheet = workbook.createSheet(protection ? "批量导入-防护" : "批量导入-性能");
            Row names = sheet.createRow(0);
            Row codes = sheet.createRow(1);
            sheet.createRow(2);
            CellStyle requiredNameStyle = headerStyle(workbook, IndexedColors.LIGHT_ORANGE, true);
            CellStyle optionalNameStyle = headerStyle(workbook, IndexedColors.LIGHT_YELLOW, true);
            CellStyle codeStyle = headerStyle(workbook, IndexedColors.LIGHT_GREEN, false);
            Sheet dictionary = createDictionarySheet(workbook);
            int dictionaryRow = 1;
            int column = 0;
            for (RadiologyCatalogService.ItemDefinition item : catalogService.getCatalog(modality)) {
                if (protection != item.code().equals("RPD")) continue;
                for (RadiologyCatalogService.FieldDefinition field : item.fields()) {
                    if (!templateField(modality, protection, field.code()) || (item.code().equals("HCR") && field.code().equals("厂家规定值/基线值"))) continue;
                    Cell name = names.createCell(column);
                    String unit = StringUtils.hasText(field.unit()) ? " [" + field.unit() + "]" : "";
                    name.setCellValue(item.name() + " / " + field.label() + unit + (field.required() ? " *" : "（可选）"));
                    name.setCellStyle(field.required() ? requiredNameStyle : optionalNameStyle);
                    Cell code = codes.createCell(column);
                    String importCode = importCode(item, field);
                    code.setCellValue(importCode);
                    code.setCellStyle(codeStyle);
                    sheet.setColumnWidth(column, Math.min(40, Math.max(16, name.getStringCellValue().length() + 2)) * 256);
                    addValidation(sheet, field, column);
                    writeDictionaryRow(dictionary, dictionaryRow++, item, field, importCode);
                    column++;
                }
            }
            if (protection) {
                for (int point = 1; point <= 6; point++) {
                    for (int reading = 1; reading <= 5; reading++) {
                        Cell name = names.createCell(column);
                        name.setCellValue("场所卫生防护 / 检测点位" + point + "第" + reading + "次读数 [nSv/h] *");
                        name.setCellStyle(requiredNameStyle);
                        Cell code = codes.createCell(column);
                        code.setCellValue("RPD" + point + "-" + reading);
                        code.setCellStyle(codeStyle);
                        sheet.setColumnWidth(column, 24 * 256);
                        addNumericValidation(sheet, column);
                        column++;
                    }
                }
            }
            sheet.createFreezePane(0, 2);
            sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(1, 2, 0, Math.max(0, column - 1)));
            names.setHeightInPoints(42);
            codes.setHeightInPoints(24);
            workbook.setSheetHidden(workbook.getSheetIndex(dictionary), true);
            workbook.setActiveSheet(workbook.getSheetIndex(sheet));
            workbook.write(output);
            return new TemplateFile(output.toByteArray(), modality + "-" + (protection ? "protection" : "performance") + "-import.xlsx");
        } catch (IOException ex) {
            throw new BusinessException("模板生成失败：" + ex.getMessage());
        }
    }

    private Sheet selectSheet(Workbook workbook, String requested, String importType) {
        if (StringUtils.hasText(requested)) {
            Sheet sheet = workbook.getSheet(requested);
            if (sheet == null) throw new BusinessException("找不到工作表：" + requested);
            return sheet;
        }
        boolean protection = "PROTECTION".equalsIgnoreCase(importType);
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            Sheet sheet = workbook.getSheetAt(i);
            String name = sheet.getSheetName();
            if (protection && name.contains("防护")) return sheet;
            if (!protection && (name.contains("性能") || name.contains("批量导入")) && !name.contains("防护")) return sheet;
        }
        return workbook.getSheetAt(0);
    }

    private boolean templateField(RadiologyModality modality, boolean protection, String code) {
        if (protection) {
            return code.equals("k") || code.equals("a") || code.equals("RPD-mA") || code.startsWith("RPD0-");
        }
        if (modality == RadiologyModality.DR) {
            return true;
        }
        return !code.equals("HUU-L3") && !code.equals("NPS-n");
    }

    private List<Row> findDataRows(Sheet sheet) {
        List<Row> rows = new ArrayList<>();
        for (int rowIndex = 2; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) continue;
            for (Cell cell : row) {
                if (StringUtils.hasText(text(cell))) {
                    rows.add(row);
                    break;
                }
            }
        }
        return rows;
    }

    private void validateRequiredGroups(
            RadiologyCase entity,
            Map<String, Set<String>> suppliedFields,
            List<RadiologyDto.MeasurementRequest> values,
            List<RadiologyDto.ImportCellError> errors) {
        Map<String, String> textValues = new HashMap<>();
        for (RadiologyDto.MeasurementRequest value : values) {
            if (StringUtils.hasText(value.getValueText())) textValues.put(value.getFieldCode(), value.getValueText().trim());
        }
        for (Map.Entry<String, Set<String>> entry : suppliedFields.entrySet()) {
            var item = catalogService.findItem(entity.getModality(), entry.getKey()).orElse(null);
            if (item == null) continue;
            boolean noResidualImage = item.code().equals("ILA") && isNo(textValues.get("是否有残影（如“否”无需填后两项）"));
            for (RadiologyCatalogService.FieldDefinition field : item.fields()) {
                if (!field.required()) continue;
                if(item.code().equals("GTA") && textValues.get("测量值α2")==null) {
                    boolean hasAngle=values.stream().anyMatch(v->v.getItemCode().equals("GTA") && v.getFieldCode().equals("测量值α2") && v.getValueNum()!=null);
                    if(hasAngle && field.code().contains("边沿间距"))continue;
                    if(!hasAngle && field.code().equals("测量值α2"))continue;
                }
                if(item.code().equals("CTDI")) {
                    String parts=caseService.itemConditions(entity).getOrDefault("__ctdi.parts",RadiologyScopeService.type(entity.getDetectionCategory()).equals("ACCEPTANCE")?"HEAD,BODY":"HEAD");
                    boolean body=field.code().matches("S[CDULR]D[123]") || List.of("层数n2","层厚T2","刻度系数k2").contains(field.code());
                    if(!parts.contains(body?"BODY":"HEAD"))continue;
                }
                if (item.code().equals("LCR") && RadiologyScopeService.type(entity.getDetectionCategory()).equals("ACCEPTANCE")
                        && field.code().equals("厂家规定值/基线值")) continue;
                if (noResidualImage && (field.code().equals("残影区D1") || field.code().equals("非残影区D0"))) continue;
                if (!entry.getValue().contains(field.code().toUpperCase(Locale.ROOT))) {
                    errors.add(new RadiologyDto.ImportCellError(1, field.code(), "", item.name() + "缺少必填字段"));
                }
            }
        }
    }

    private boolean isNo(String value) {
        return value != null && (value.equalsIgnoreCase("no") || value.equals("否") || value.equals("无"));
    }

    private void createInstructions(Workbook workbook, RadiologyModality modality, boolean protection) {
        Sheet sheet = workbook.createSheet("填写说明");
        Font titleFont = workbook.createFont();
        titleFont.setBold(true);
        titleFont.setFontHeightInPoints((short) 16);
        CellStyle titleStyle = workbook.createCellStyle();
        titleStyle.setFont(titleFont);
        CellStyle labelStyle = headerStyle(workbook, IndexedColors.LIGHT_CORNFLOWER_BLUE, true);
        sheet.createRow(0).createCell(0).setCellValue("BHFYZX 放射诊疗检测数据导入模板");
        sheet.getRow(0).getCell(0).setCellStyle(titleStyle);
        String[][] values = {
                {"模板版本", TEMPLATE_VERSION},
                {"设备类型", modality.name()},
                {"导入类型", protection ? "场所卫生防护" : "设备性能"},
                {"标准依据", protection ? "GBZ 130—2020" : modality == RadiologyModality.DR ? "WS 76—2020" : "WS 519—2019"},
                {"填写规则", "仅在批量导入工作表第 3 行填写一行原始数据；每个项目使用一个文件，禁止公式。"},
                {"颜色说明", "橙色为必填字段，黄色为可选修正/基线字段，绿色为系统字段代码。"},
                {"计算说明", "Excel 仅采集原始数据；正式计算、单位换算、判定和报告均以系统结果为准。"},
                {"导入步骤", "先在系统执行“预检”，确认无错误后再执行“正式导入”。"}
        };
        for (int index = 0; index < values.length; index++) {
            Row row = sheet.createRow(index + 2);
            Cell label = row.createCell(0);
            label.setCellValue(values[index][0]);
            label.setCellStyle(labelStyle);
            row.createCell(1).setCellValue(values[index][1]);
        }
        sheet.setColumnWidth(0, 18 * 256);
        sheet.setColumnWidth(1, 90 * 256);
    }

    private Sheet createDictionarySheet(Workbook workbook) {
        Sheet sheet = workbook.createSheet("_字段字典");
        Row header = sheet.createRow(0);
        String[] names = {"项目代码", "项目名称", "字段代码", "字段名称", "单位", "必填", "标准"};
        for (int i = 0; i < names.length; i++) header.createCell(i).setCellValue(names[i]);
        return sheet;
    }

    private void writeDictionaryRow(
            Sheet sheet,
            int rowIndex,
            RadiologyCatalogService.ItemDefinition item,
            RadiologyCatalogService.FieldDefinition field,
            String importCode) {
        Row row = sheet.createRow(rowIndex);
        row.createCell(0).setCellValue(item.code());
        row.createCell(1).setCellValue(item.name());
        row.createCell(2).setCellValue(importCode);
        row.createCell(3).setCellValue(field.label());
        row.createCell(4).setCellValue(field.unit());
        row.createCell(5).setCellValue(field.required() ? "是" : "否");
        row.createCell(6).setCellValue(item.standardCode());
    }

    private String importCode(
            RadiologyCatalogService.ItemDefinition item,
            RadiologyCatalogService.FieldDefinition field) {
        if (field.code().equals("厂家规定值/基线值")) {
            if (item.code().equals("HCR")) return "HCR-LEGACY-REFERENCE";
            if (item.code().equals("LCR")) return "LCR-BASELINE";
        }
        return field.code();
    }

    private String canonicalFieldCode(String itemCode, String importCode) {
        if ((itemCode.equals("HCR") && importCode.equalsIgnoreCase("HCR-LEGACY-REFERENCE"))
                || (itemCode.equals("LCR") && importCode.equalsIgnoreCase("LCR-BASELINE"))) {
            return "厂家规定值/基线值";
        }
        return importCode;
    }

    private CellStyle headerStyle(Workbook workbook, IndexedColors color, boolean bold) {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(color.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setWrapText(true);
        style.setAlignment(HorizontalAlignment.CENTER);
        Font font = workbook.createFont();
        font.setBold(bold);
        style.setFont(font);
        return style;
    }

    private void addValidation(Sheet sheet, RadiologyCatalogService.FieldDefinition field, int column) {
        if (field.code().equals("是否有残影（如“否”无需填后两项）") || field.code().equals("填“是”或“否”")) {
            DataValidationHelper helper = sheet.getDataValidationHelper();
            DataValidationConstraint constraint = helper.createExplicitListConstraint(new String[] {"是", "否"});
            DataValidation validation = helper.createValidation(constraint, new CellRangeAddressList(2, 2, column, column));
            validation.setShowErrorBox(true);
            validation.createErrorBox("输入错误", "只能选择“是”或“否”");
            sheet.addValidationData(validation);
        } else {
            addNumericValidation(sheet, column);
        }
    }

    private void addNumericValidation(Sheet sheet, int column) {
        DataValidationHelper helper = sheet.getDataValidationHelper();
        DataValidationConstraint constraint = helper.createDecimalConstraint(
                DataValidationConstraint.OperatorType.BETWEEN, "-1000000000000", "1000000000000");
        DataValidation validation = helper.createValidation(constraint, new CellRangeAddressList(2, 2, column, column));
        validation.setShowErrorBox(true);
        validation.createErrorBox("输入错误", "请输入数值，不要填写单位或公式");
        sheet.addValidationData(validation);
    }

    private String text(Cell cell) {
        if (cell == null) return "";
        if (cell.getCellType() == CellType.FORMULA) return dataFormatter.formatCellValue(cell);
        return dataFormatter.formatCellValue(cell);
    }

    public record TemplateFile(byte[] bytes, String fileName) {
    }
}
