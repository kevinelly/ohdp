package com.dsf.radiology.service;

import com.dsf.auth.security.AuthContext;
import com.dsf.common.exception.BusinessException;
import com.dsf.radiology.config.RadiologyStorageProperties;
import com.dsf.radiology.dto.RadiologyDto;
import com.dsf.radiology.entity.RadiologyAttachment;
import com.dsf.radiology.entity.RadiologyCase;
import com.dsf.radiology.entity.RadiologyMeasurement;
import com.dsf.radiology.entity.RadiologyProtectionPoint;
import com.dsf.radiology.entity.RadiologyReport;
import com.dsf.radiology.model.RadiologyCaseStatus;
import com.dsf.radiology.model.RadiologyFieldRole;
import com.dsf.radiology.model.RadiologyJudgement;
import com.dsf.radiology.repository.RadiologyAttachmentRepository;
import com.dsf.radiology.repository.RadiologyCaseRepository;
import com.dsf.radiology.repository.RadiologyMeasurementRepository;
import com.dsf.radiology.repository.RadiologyProtectionPointRepository;
import com.dsf.radiology.repository.RadiologyReportRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.BreakType;
import org.apache.poi.xwpf.usermodel.Document;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.TableRowAlign;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFFooter;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTFldChar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageSz;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STFldCharType;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class RadiologyReportService {

    private static final String FONT = "宋体";
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final RadiologyReportRepository reportRepository;
    private final RadiologyCaseRepository caseRepository;
    private final RadiologyMeasurementRepository measurementRepository;
    private final RadiologyProtectionPointRepository protectionPointRepository;
    private final RadiologyAttachmentRepository attachmentRepository;
    private final RadiologyCaseService caseService;
    private final RadiologyCatalogService catalogService;
    private final RadiologyStandardEvaluator standardEvaluator;
    private final RadiologyStorageProperties properties;
    private final ObjectMapper objectMapper;

    @Transactional
    public RadiologyDto.ReportResponse generate(Long caseId) {
        caseService.requirePermission("RADIOLOGY:REPORT:GENERATE");
        RadiologyCase live = caseService.requireCase(caseId);
        RadiologyCase entity=live;
        if (entity.getStatus() != RadiologyCaseStatus.APPROVED && entity.getStatus() != RadiologyCaseStatus.REPORTED) {
            throw new BusinessException("报告仅可在批准后生成");
        }
        if (!StringUtils.hasText(live.getApprovedSnapshotJson()))
            throw new BusinessException("历史案例未保存批准快照，请创建修订版重新计算和审批；原报告仍可下载");
        RadiologyCaseService.ApprovedSnapshot approved;
        try { approved=objectMapper.readValue(live.getApprovedSnapshotJson(),RadiologyCaseService.ApprovedSnapshot.class); }
        catch (JsonProcessingException ex) { throw new BusinessException("批准快照读取失败"); }
        entity=approved.inspectionCase();
        List<RadiologyMeasurement> measurements=approved.measurements();
        List<RadiologyProtectionPoint> points=approved.protectionPoints();
        List<RadiologyAttachment> attachments=approved.attachments();
        if (!templateHash(entity.getModality()).equals(approved.templateSha256()))
            throw new BusinessException("模板与批准版本不一致，请恢复批准时模板后生成报告");
        String reportNo=entity.getSampleAcceptanceNo();
        if (!StringUtils.hasText(reportNo)) throw new BusinessException("批准快照缺少受理编号");
        String fileName = safeName(reportNo) + "-R" + entity.getRevisionNo() + "-检测报告-" + LocalDateTime.now().format(FILE_TIME) + "-" + java.util.UUID.randomUUID().toString().substring(0,8) + ".docx";
        Path root = Path.of(properties.getBaseDir()).toAbsolutePath().normalize();
        Path directory = root.resolve(String.valueOf(caseId)).resolve("reports").normalize();
        Path target = directory.resolve(fileName).normalize();
        if (!target.startsWith(root)) throw new BusinessException("报告存储路径不合法");
        try {
            Files.createDirectories(directory);
            try (XWPFDocument document = new XWPFDocument(new org.springframework.core.io.ClassPathResource(templatePath(entity.getModality())).getInputStream())) {
                replaceTemplateFields(document,entity,reportNo);
                buildBasicInfoPage(document, entity);
                document.createParagraph().createRun().addBreak(BreakType.PAGE);
                buildResultsPage(document, entity, measurements, points);
                buildConclusionPage(document, entity, measurements, points, attachments);

                try (var output = Files.newOutputStream(target)) {
                    document.write(output);
                }
            }
        } catch (IOException ex) {
            throw new BusinessException("报告生成失败：" + ex.getMessage());
        }

        String snapshot = live.getApprovedSnapshotJson();
        RadiologyReport report = new RadiologyReport();
        report.setCaseId(caseId);
        report.setReportNo(reportNo);
        report.setSnapshotJson(snapshot);
        report.setFileName(fileName);
        report.setStoragePath(target.toString());
        report.setSha256(sha256(target));
        report.setGeneratedBy(AuthContext.get() == null ? "system" : AuthContext.get().getUsername());
        report.setGeneratedAt(LocalDateTime.now());
        report = reportRepository.save(report);
        live.setStatus(RadiologyCaseStatus.REPORTED);
        caseRepository.save(live);
        return toResponse(report);
    }

    @Transactional(readOnly = true)
    public List<RadiologyDto.ReportResponse> list(Long caseId) {
        caseService.requireCase(caseId);
        return reportRepository.findByCaseIdAndDeletedFalseOrderByGeneratedAtDesc(caseId).stream()
                .map(RadiologyReportService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public Download download(Long id) {
        RadiologyReport report = reportRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BusinessException("报告不存在"));
        Path root = Path.of(properties.getBaseDir()).toAbsolutePath().normalize();
        Path path = Path.of(report.getStoragePath()).toAbsolutePath().normalize();
        if (!path.startsWith(root)) throw new BusinessException("报告路径越界");
        if (!Files.isRegularFile(path)) throw new BusinessException("报告文件不存在");
        return new Download(new FileSystemResource(path), report.getFileName(), Files.exists(path) ? path.toFile().length() : 0);
    }

    private static String templatePath(com.dsf.radiology.model.RadiologyModality modality) {
        return "templates/radiology/"+modality.name()+"-v20261013.docx";
    }
    public static String templateHash(com.dsf.radiology.model.RadiologyModality modality) {
        try (var input=new org.springframework.core.io.ClassPathResource(templatePath(modality)).getInputStream()) {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.readAllBytes()));
        } catch (Exception ex) { throw new BusinessException("批准模板不可用"); }
    }
    private void replaceTemplateFields(XWPFDocument document,RadiologyCase entity,String reportNo) {
        Map<String,String> values=Map.of("{{REPORT_NO}}",reportNo,"{{UNIT}}",blank(entity.getInspectedUnitName()),
                "{{EQUIPMENT}}",blank(entity.getEquipmentName()),"{{MODEL}}",blank(entity.getEquipmentModel()),
                "{{EQUIPMENT_NO}}",blank(entity.getEquipmentNo()),"{{AGENCY}}",blank(entity.getAgencyName()),
                "{{DATE}}",date(entity.getReportDate()),"{{DETECTION_DATE}}",date(entity.getInspectionDate()));
        List<XWPFParagraph> paragraphs=new ArrayList<>(document.getParagraphs());
        for (var table:document.getTables()) for(var row:table.getRows()) for(var cell:row.getTableCells()) paragraphs.addAll(cell.getParagraphs());
        for (var header:document.getHeaderList()) paragraphs.addAll(header.getParagraphs());
        for (var footer:document.getFooterList()) paragraphs.addAll(footer.getParagraphs());
        for(var paragraph:paragraphs) for(var run:paragraph.getRuns()) {
            String text=run.getText(0);
            if(text!=null) { for(var value:values.entrySet()) text=text.replace(value.getKey(),value.getValue());run.setText(text,0); }
        }
    }

    private void buildCover(XWPFDocument document, RadiologyCase entity, String reportNo) {
        spacer(document, 4);
        title(document, "放射诊疗设备质量控制检测报告", 26);
        centerText(document, entity.getModality() + " 检测", 18, true);
        spacer(document, 3);
        XWPFTable table = document.createTable(6, 2);
        table.setWidth("75%");
        table.setTableAlignment(TableRowAlign.CENTER);
        setTableCell(table.getRow(0).getCell(0), "报告编号", true);
        setTableCell(table.getRow(0).getCell(1), reportNo, false);
        setTableCell(table.getRow(1).getCell(0), "受检单位", true);
        setTableCell(table.getRow(1).getCell(1), blank(entity.getInspectedUnitName()), false);
        setTableCell(table.getRow(2).getCell(0), "设备名称", true);
        setTableCell(table.getRow(2).getCell(1), blank(entity.getEquipmentName()), false);
        setTableCell(table.getRow(3).getCell(0), "设备型号", true);
        setTableCell(table.getRow(3).getCell(1), blank(entity.getEquipmentModel()), false);
        setTableCell(table.getRow(4).getCell(0), "设备编号", true);
        setTableCell(table.getRow(4).getCell(1), blank(entity.getEquipmentNo()), false);
        setTableCell(table.getRow(5).getCell(0), "检测日期", true);
        setTableCell(table.getRow(5).getCell(1), date(entity.getInspectionDate()), false);
        spacer(document, 5);
        centerText(document, "本报告检测数据与审批状态均已固化存档", 11, false);
        centerText(document, "未经书面批准，不得部分复制本报告", 11, false);
    }

    private void buildBasicInfoPage(XWPFDocument document, RadiologyCase entity) {
        heading(document, "一、基本信息", 16);
        XWPFTable info = document.createTable(11, 4);
        info.setWidth("100%");
        String[][] values = {
                {"受检单位", blank(entity.getInspectedUnitName()), "单位地址", blank(entity.getInspectedUnitAddress())},
                {"联系人", blank(entity.getContactName()), "联系电话", blank(entity.getContactPhone())},
                {"设备名称", blank(entity.getEquipmentName()), "设备型号", blank(entity.getEquipmentModel())},
                {"设备编号", blank(entity.getEquipmentNo()), "生产厂家", blank(entity.getManufacturer())},
                {"检测模体", blank(entity.getPhantomName()), "模体厂家", blank(entity.getPhantomManufacturer())},
                {"模体型号", blank(entity.getPhantomModel()), "模体编号", blank(entity.getPhantomSerialNo())},
                {"检测类别", blank(entity.getDetectionCategory()), "检测地点", blank(entity.getDetectionLocation())},
                {"受理编号", blank(entity.getSampleAcceptanceNo()), "案例编号", blank(entity.getCaseCode())},
                {"检测日期", date(entity.getInspectionDate()), "报告日期", date(entity.getReportDate())},
                {"计算规则", blank(entity.getCalculationVersion()), "状态", entity.getStatus().name()},
                {"修订版本", String.valueOf(entity.getRevisionNo()), "设备类型", entity.getModality().name()}
        };
        for (int row = 0; row < values.length; row++) {
            for (int column = 0; column < 4; column++) setTableCell(info.getRow(row).getCell(column), values[row][column], column % 2 == 0);
        }
        heading(document, "二、检测依据", 16);
        body(document, blank(entity.getStandardBasis()));
        heading(document, "三、检测仪器", 16);
        body(document, blank(entity.getInstrumentDescription()));
        heading(document, "四、说明", 16);
        body(document, "计算过程采用版本化规则，原始值、中间值和最终值分别保存；厂家规定值、检测基线、模体参数及换算系数随案例快照保存。"
                + (StringUtils.hasText(entity.getPhantomManualReference()) ? "模体说明书/依据：" + entity.getPhantomManualReference() + "。" : ""));
    }

    private void buildResultsPage(
            XWPFDocument document,
            RadiologyCase entity,
            List<RadiologyMeasurement> measurements,
            List<RadiologyProtectionPoint> points) {
        heading(document, "五、设备质量控制检测结果", 16);
        Map<String, RadiologyMeasurement> finals = finalMeasurements(measurements);
        XWPFTable table = document.createTable(1, 5);
        table.setWidth("100%");
        String[] headers = {"检测项目", "检测条件", "检测结果", "标准要求", "评价"};
        for (int i=0;i<headers.length;i++) setTableCell(table.getRow(0).getCell(i),headers[i],true);
        table.getRow(0).setRepeatHeader(true);
        var results=caseService.itemResults(entity);
        var conditions=caseService.itemConditions(entity);
        for (var item:catalogService.getCatalog(entity.getModality())) {
            if (item.code().equals("RPD")) continue;
            var result=results.stream().filter(r->r.itemCode().equals(item.code())).findFirst().orElse(null);
            if (result==null) throw new BusinessException("批准快照缺少项目："+item.name());
            var cells=table.createRow().getTableCells();
            setTableCell(cells.get(0),item.name(),false);
            setTableCell(cells.get(1),conditions.getOrDefault(item.code(),entity.getDetectionCategory()),false);
            setTableCell(cells.get(2),result.resultText()!=null ? result.resultText() :
                    number(result.resultValue())+(StringUtils.hasText(result.resultUnit()) ? " "+result.resultUnit():""),false);
            setTableCell(cells.get(3),result.ruleDescription(),false);
            setTableCell(cells.get(4),conclusion(result.judgement()),false);
        }

        document.createParagraph().createRun().addBreak(BreakType.PAGE);
        heading(document, "六、场所卫生防护检测结果", 16);
        XWPFTable pointTable = document.createTable(1, 8);
        pointTable.setWidth("100%");
        pointTable.getRow(0).setRepeatHeader(true);
        String[] pointHeaders = {"点位", "位置描述", "原始读数", "平均值", "结果(μSv/h)", "年工作负荷(mAs/a)", "年有效剂量(mSv/a)", "控制值/结论"};
        for (int i = 0; i < pointHeaders.length; i++) setTableCell(pointTable.getRow(0).getCell(i), pointHeaders[i], true);
        for (RadiologyProtectionPoint point : points) {
            XWPFTableCell[] cells = pointTable.createRow().getTableCells().toArray(XWPFTableCell[]::new);
            setTableCell(cells[0], point.getPointName(), false);
            setTableCell(cells[1], blank(point.getLocationDescription()), false);
            setTableCell(cells[2], point.getReadingsJson(), false);
            setTableCell(cells[3], number(point.getAverageValue()), false);
            setTableCell(cells[4], number(point.getResultValue()), false);
            setTableCell(cells[5], number(point.getAnnualWorkloadMas()), false);
            setTableCell(cells[6], number(point.getAnnualEffectiveDose()), false);
            String limitText = "≤" + number(point.getControlLimit()) + " μSv/h";
            if (point.getAnnualDoseLimit() != null) limitText += "；年剂量≤" + number(point.getAnnualDoseLimit()) + " mSv/a";
            setTableCell(cells[7], limitText + " / " + conclusion(point.getJudgement()), false);
        }
        body(document,"防护数据处理："+switch(entity.getProtectionMode()==null ? "UNSUBTRACTED":entity.getProtectionMode()) {
            case "SUBTRACTED" -> "已扣除本底";
            case "RESPONSE_CORRECTED" -> "已扣除本底并按记录的k2进行响应时间修正";
            default -> "未扣除本底";
        }+"；原始读数单位："+blank(entity.getRpdUnit())+"；CT控制值≤2.5 μSv/h，DR短时摄影控制值≤25 μSv/h，超过时另评价年有效剂量。");
    }

    private void buildConclusionPage(
            XWPFDocument document,
            RadiologyCase entity,
            List<RadiologyMeasurement> measurements,
            List<RadiologyProtectionPoint> points,
            List<RadiologyAttachment> attachments) {
        heading(document, "七、检测点位分布图", 16);
        Optional<RadiologyAttachment> pointMap = attachments.stream().filter(value -> value.getCategory().equals("POINT_MAP")).findFirst();
        if (pointMap.isPresent()) {
            addImage(document, pointMap.get());
        } else {
            body(document, "未上传检测点位分布图。");
        }
        heading(document, "八、综合评价", 16);
        boolean pointFailure = points.stream().anyMatch(value -> value.getJudgement() == RadiologyJudgement.UNQUALIFIED);
        boolean pointAssessment = points.stream().anyMatch(value -> value.getJudgement() == RadiologyJudgement.NEEDS_ASSESSMENT);
        boolean itemFailure = finalMeasurements(measurements).entrySet().stream()
                .anyMatch(entry -> ruleView(entity, entry.getKey(), entry.getValue(), measurements).conclusion().equals("不合格"));
        String evaluation;
        if (pointFailure || itemFailure) {
            evaluation = "本次检测存在不符合标准控制要求的项目，详见检测结果表；应完成整改并复测。";
        } else if (pointAssessment) {
            evaluation = "DR 机房存在周围剂量当量率超过 25 μSv/h 的点位，应按 GBZ 130—2020 进一步进行年有效剂量评估；在评估确认不超过 0.25 mSv 前，不作最终合格结论。";
        } else {
            evaluation = "本次已完成且具备判定条件的项目未发现不符合标准控制要求的结果；需厂家、模体或基线资料的项目不纳入最终自动合格判定。";
        }
        body(document, evaluation);
        if (StringUtils.hasText(entity.getNotes())) {
            heading(document, "九、备注", 16);
            body(document, entity.getNotes());
        }
        spacer(document, 2);
        XWPFTable signatures = document.createTable(3, 4);
        signatures.setWidth("100%");
        String[][] values = {
                {"编写人", blank(entity.getWriterName()), "日期", date(entity.getReportDate())},
                {"审核人", blank(entity.getReviewerName()), "审核意见", blank(entity.getReviewComment())},
                {"批准人", blank(entity.getApproverName()), "批准意见", blank(entity.getApprovalComment())}
        };
        for (int row = 0; row < values.length; row++) {
            for (int column = 0; column < 4; column++) setTableCell(signatures.getRow(row).getCell(column), values[row][column], column % 2 == 0);
        }
    }

    private Map<String, RadiologyMeasurement> finalMeasurements(List<RadiologyMeasurement> measurements) {
        Map<String, RadiologyMeasurement> result = new LinkedHashMap<>();
        for (RadiologyMeasurement value : measurements) {
            if (value.getFieldRole() != RadiologyFieldRole.FINAL) continue;
            RadiologyMeasurement current = result.get(value.getItemCode());
            if (current == null || value.getFieldCode().equals("RESULT")) result.put(value.getItemCode(), value);
        }
        return result;
    }

    private RuleView ruleView(
            RadiologyCase entity, String itemCode, RadiologyMeasurement result, List<RadiologyMeasurement> measurements) {
        var evaluation=caseService.itemResults(entity).stream().filter(r->r.itemCode().equals(itemCode)).findFirst()
                .orElseThrow(()->new BusinessException("批准快照缺少判定："+itemCode));
        return new RuleView(evaluation.ruleDescription(),conclusion(evaluation.judgement()));
    }

    private String snapshot(
            RadiologyCase entity,
            List<RadiologyMeasurement> measurements,
            List<RadiologyProtectionPoint> points,
            List<RadiologyAttachment> attachments) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("snapshotVersion", RadiologyCatalogService.CALCULATION_VERSION);
        snapshot.put("generatedAt", LocalDateTime.now());
        snapshot.put("case", caseService.getCase(entity.getId()));
        snapshot.put("measurementCount", measurements.size());
        snapshot.put("protectionPointCount", points.size());
        snapshot.put("attachmentHashes", attachments.stream().collect(
                java.util.stream.Collectors.toMap(RadiologyAttachment::getOriginalName, RadiologyAttachment::getSha256, (left, right) -> right)));
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException ex) {
            throw new BusinessException("报告快照创建失败：" + ex.getMessage());
        }
    }

    private void configurePage(XWPFDocument document) {
        CTSectPr section = document.getDocument().getBody().isSetSectPr()
                ? document.getDocument().getBody().getSectPr() : document.getDocument().getBody().addNewSectPr();
        CTPageSz size = section.isSetPgSz() ? section.getPgSz() : section.addNewPgSz();
        size.setW(11906);
        size.setH(16838);
        CTPageMar margin = section.isSetPgMar() ? section.getPgMar() : section.addNewPgMar();
        margin.setTop(1134);
        margin.setBottom(1134);
        margin.setLeft(1134);
        margin.setRight(1134);
        margin.setHeader(567);
        margin.setFooter(567);
    }

    private void addFooter(XWPFDocument document) {
        XWPFFooter footer = document.createFooter(org.apache.poi.wp.usermodel.HeaderFooterType.DEFAULT);
        XWPFParagraph paragraph = footer.createParagraph();
        paragraph.setAlignment(ParagraphAlignment.CENTER);
        run(paragraph, "第 ", 9, false);
        XWPFRun page = run(paragraph, "", 9, false);
        CTFldChar begin = page.getCTR().addNewFldChar();
        begin.setFldCharType(STFldCharType.BEGIN);
        page.getCTR().addNewInstrText().setStringValue(" PAGE ");
        CTFldChar end = page.getCTR().addNewFldChar();
        end.setFldCharType(STFldCharType.END);
        run(paragraph, " 页", 9, false);
    }

    private void addImage(XWPFDocument document, RadiologyAttachment attachment) {
        Path root = Path.of(properties.getBaseDir()).toAbsolutePath().normalize();
        Path path = Path.of(attachment.getStoragePath()).toAbsolutePath().normalize();
        if (!path.startsWith(root) || !Files.isRegularFile(path)) {
            body(document, "点位图文件不可用：" + attachment.getOriginalName());
            return;
        }
        int pictureType = switch (attachment.getMediaType()) {
            case "image/jpeg" -> Document.PICTURE_TYPE_JPEG;
            case "image/png" -> Document.PICTURE_TYPE_PNG;
            default -> Document.PICTURE_TYPE_PNG;
        };
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setAlignment(ParagraphAlignment.CENTER);
        try (InputStream input = Files.newInputStream(path)) {
            var pixels=javax.imageio.ImageIO.read(path.toFile());
            if (pixels==null) throw new IllegalArgumentException("图片内容无效");
            double scale=Math.min(460.0/pixels.getWidth(),360.0/pixels.getHeight());
            run(paragraph, "", 10, false).addPicture(input, pictureType, attachment.getOriginalName(), Units.toEMU(pixels.getWidth()*scale), Units.toEMU(pixels.getHeight()*scale));
        } catch (Exception ex) {
            body(document, "点位图插入失败：" + attachment.getOriginalName());
        }
    }

    private void title(XWPFDocument document, String text, int size) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setAlignment(ParagraphAlignment.CENTER);
        paragraph.setSpacingAfter(320);
        run(paragraph, text, size, true);
    }

    private void centerText(XWPFDocument document, String text, int size, boolean bold) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setAlignment(ParagraphAlignment.CENTER);
        run(paragraph, text, size, bold);
    }

    private void heading(XWPFDocument document, String text, int size) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setSpacingBefore(180);
        paragraph.setSpacingAfter(100);
        run(paragraph, text, size, true);
    }

    private void body(XWPFDocument document, String text) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setIndentationFirstLine(420);
        paragraph.setSpacingBetween(1.5);
        run(paragraph, text, 11, false);
    }

    private void spacer(XWPFDocument document, int lines) {
        for (int i = 0; i < lines; i++) document.createParagraph();
    }

    private void setTableCell(XWPFTableCell cell, String text, boolean bold) {
        cell.getTableRow().setCantSplitRow(true);
        cell.setText("");
        XWPFParagraph paragraph = cell.getParagraphs().get(0);
        paragraph.setAlignment(ParagraphAlignment.CENTER);
        paragraph.setSpacingAfter(0);
        paragraph.setSpacingBefore(0);
        run(paragraph, blank(text), 9, bold);
        cell.setVerticalAlignment(XWPFTableCell.XWPFVertAlign.CENTER);
        if (bold) cell.setColor("EAF2F8");
    }

    private XWPFRun run(XWPFParagraph paragraph, String text, int size, boolean bold) {
        XWPFRun run = paragraph.createRun();
        run.setText(text);
        run.setFontFamily(FONT);
        run.setFontSize(size);
        run.setBold(bold);
        run.getCTR().addNewRPr().addNewRFonts().setEastAsia(FONT);
        return run;
    }

    private String display(RadiologyMeasurement result) {
        if (StringUtils.hasText(result.getValueText())) return result.getValueText();
        return number(result.getValueNum()) + (StringUtils.hasText(result.getUnit()) ? " " + result.getUnit() : "");
    }

    private String number(BigDecimal value) {
        return value == null ? "—" : value.setScale(6,java.math.RoundingMode.HALF_EVEN).stripTrailingZeros().toPlainString();
    }

    private String date(LocalDate value) {
        return value == null ? "—" : value.toString();
    }

    private String blank(String value) {
        return StringUtils.hasText(value) ? value : "—";
    }

    private String conclusion(RadiologyJudgement value) {
        if (value == null) return "未判定";
        return switch (value) {
            case QUALIFIED -> "合格";
            case UNQUALIFIED -> "不合格";
            case INCOMPLETE -> "数据不完整";
            case NOT_CONFIGURED -> "未配置";
            case NOT_APPLICABLE -> "不适用";
            case BASELINE_ESTABLISHED -> "建立基线";
            case NEEDS_ASSESSMENT -> "需进一步评估";
        };
    }

    private String safeName(String value) {
        return value.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private String sha256(Path path) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(path); DigestInputStream stream = new DigestInputStream(input, digest)) {
                stream.transferTo(java.io.OutputStream.nullOutputStream());
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException | NoSuchAlgorithmException ex) {
            throw new BusinessException("报告校验失败：" + ex.getMessage());
        }
    }

    public static RadiologyDto.ReportResponse toResponse(RadiologyReport value) {
        return new RadiologyDto.ReportResponse(
                value.getId(), value.getCaseId(), value.getReportNo(), value.getFileName(), value.getSha256(),
                value.getGeneratedBy(), value.getGeneratedAt());
    }

    public record Download(Resource resource, String fileName, long fileSize) {
    }

    private record RuleView(String basis, String conclusion) {
    }
}
