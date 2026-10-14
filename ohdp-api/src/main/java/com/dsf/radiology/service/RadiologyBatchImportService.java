package com.dsf.radiology.service;

import com.dsf.common.exception.BusinessException;
import com.dsf.radiology.dto.RadiologyDto;
import com.dsf.radiology.model.*;
import com.dsf.radiology.repository.RadiologyCaseRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.io.*;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** New cases only: a duplicate case code never silently overwrites approved or draft data. */
@Service @RequiredArgsConstructor
public class RadiologyBatchImportService {
    private final RadiologyCaseService cases;
    private final RadiologyCaseRepository repository;
    private final RadiologyCatalogService catalog;
    private static final List<String> HEADERS=List.of("CASE_CODE","MODALITY","RECEIPT","UNIT_NAME","EQUIPMENT_NAME","CATEGORY","START_DATE","END_DATE","ITEM_CODE","ROW_NO","CONDITION","FIELD_CODE","VALUE");
    public record RowStatus(int row,String caseCode,String status,String message) {}
    public record Preview(boolean dryRun,int cases,int appliedCases,List<RowStatus> rows) {}
    private static class Plan {
        RadiologyDto.CaseRequest request;List<RadiologyDto.MeasurementRequest> values=new ArrayList<>();
        Map<String,String> conditions=new LinkedHashMap<>();Map<String,Set<Integer>> ids=new LinkedHashMap<>();Set<String> keys=new HashSet<>();
    }
    public byte[] template() {
        try(var workbook=new XSSFWorkbook();var out=new ByteArrayOutputStream()) {
            var help=workbook.createSheet("填写说明");
            String[] instructions={"多案例批量导入：每行一项原始字段，同一个CASE_CODE归入同一案例；仅新增案例，已有编号将整批拒绝。",
                "先预检再正式导入；任意一行有错误，整批不写入。每批最多3000行。",
                "每个案例第一行填写前8列，后续行重复CASE_CODE，其余基本信息可留空；日期使用文本YYYY-MM-DD。",
                "MODALITY填CT或DR，CATEGORY填验收检测、状态检测或稳定性检测。",
                "ITEM_CODE、FIELD_CODE参见字段目录；VALUE只填原始数值或文字，不接受公式。",
                "ROW_NO可选：TVD、ROA、ETD、HCR、LCR、RST可填1至999，每项目最多30行；TVD/ETD使用第1组字段代码，每个ROW_NO代表一组。",
                "CONDITION填写对应项目或测量行条件；测量数据可后续补录，导入不自动计算、审核或出报告。",
                "CTDI部位选择可用ITEM_CODE=CTDI、FIELD_CODE=PARTS、VALUE=HEAD或BODY或HEAD,BODY。状态/稳定性仅HEAD。",
                "防护点位和附件在案例建立后从对应页面导入或上传。不得在工作簿中填真实签名和印章。"};
            for(int i=0;i<instructions.length;i++)help.createRow(i).createCell(0).setCellValue(instructions[i]);help.setColumnWidth(0,25000);
            var sheet=workbook.createSheet("批量案例");var header=sheet.createRow(0);for(int i=0;i<HEADERS.size();i++){header.createCell(i).setCellValue(HEADERS.get(i));sheet.setColumnWidth(i,6000);}sheet.createFreezePane(0,1);
            var fields=workbook.createSheet("字段目录");int n=0;var title=fields.createRow(n++);String[] labels={"设备类别","项目代码","字段代码","含义","单位"};for(int i=0;i<labels.length;i++)title.createCell(i).setCellValue(labels[i]);
            for(var modality:RadiologyModality.values())for(var item:catalog.getCatalog(modality))for(var field:item.fields()) {var r=fields.createRow(n++);String[] values={modality.name(),item.code(),field.code(),field.label(),field.unit()};for(int i=0;i<values.length;i++)r.createCell(i).setCellValue(values[i]);}
            for(int i=0;i<5;i++)fields.setColumnWidth(i,7000);workbook.write(out);return out.toByteArray();
        }catch(IOException ex){throw new BusinessException("批量模板生成失败");}
    }
    @Transactional
    public Preview importWorkbook(MultipartFile file,boolean dryRun) {
        if(file==null || file.isEmpty() || file.getSize()>10*1024*1024) throw new BusinessException("请选择不超过10MB的Excel工作簿");
        var plans=new LinkedHashMap<String,Plan>();var statuses=new ArrayList<RowStatus>();boolean invalid=false;
        try(var workbook=WorkbookFactory.create(file.getInputStream())) {
            var sheet=workbook.getSheet("批量案例");if(sheet==null || sheet.getRow(0)==null)throw new BusinessException("缺少批量案例工作表，请下载批量模板");
            if(sheet.getLastRowNum()>3000)throw new BusinessException("每批最多3000行");
            var formatter=new DataFormatter(Locale.CHINA);var columns=new LinkedHashMap<String,Integer>();
            for(var cell:sheet.getRow(0)){String name=formatter.formatCellValue(cell).trim();if(columns.put(name,cell.getColumnIndex())!=null)throw new BusinessException("重复表头："+name);}
            if(!columns.keySet().containsAll(HEADERS))throw new BusinessException("批量模板字段不完整，请重新下载模板");
            for(int r=1;r<=sheet.getLastRowNum();r++) {
                var row=sheet.getRow(r);if(row==null)continue;
                Map<String,String> data=new LinkedHashMap<>();boolean formula=false;
                for(String name:HEADERS){var cell=row.getCell(columns.get(name));formula|=cell!=null && cell.getCellType()==CellType.FORMULA;data.put(name,formatter.formatCellValue(cell).trim());}
                if(data.values().stream().allMatch(String::isBlank))continue;
                String code=data.get("CASE_CODE");
                try {
                    if(formula)throw new IllegalArgumentException("不得使用公式，请粘贴原始数值");
                    if(code.isBlank() || code.length()>64)throw new IllegalArgumentException("案例编号必填且不超过64字");
                    if(repository.existsByCaseCode(code))throw new IllegalArgumentException("案例编号已存在，不覆盖原数据");
                    Plan plan=plans.get(code);
                    if(plan==null){plan=new Plan();plan.request=request(data);plans.put(code,plan);}
                    else validateRepeatedMetadata(plan.request,data);
                    String item=data.get("ITEM_CODE"),field=data.get("FIELD_CODE"),raw=data.get("VALUE");
                    if(item.isBlank() && field.isBlank() && raw.isBlank()) {statuses.add(new RowStatus(r+1,code,"READY","仅建立案例"));continue;}
                    if(item.equals("CTDI") && field.equals("PARTS")) {
                        if(!List.of("HEAD","BODY","HEAD,BODY").contains(raw) || (!plan.request.getDetectionCategory().equals("验收检测") && !raw.equals("HEAD")))throw new IllegalArgumentException("CTDI部位选择无效");
                        if(!plan.keys.add("CTDI::PARTS"))throw new IllegalArgumentException("CTDI部位重复填写");
                        plan.conditions.put("__ctdi.parts",raw);
                    } else {
                        String rowNo=data.get("ROW_NO");String canonical=field;
                        if(!rowNo.isBlank()) {
                            if(!RadiologyMeasurementRows.ITEMS.contains(item))throw new IllegalArgumentException("此项目不支持分行录入");
                            int id=Integer.parseInt(rowNo);if(id<1 || id>999)throw new IllegalArgumentException("测量行号应为1至999");
                            canonical="R"+id+"__"+field;var ids=plan.ids.computeIfAbsent(item,k->new TreeSet<>());ids.add(id);if(ids.size()>30)throw new IllegalArgumentException("每项目最多30行");
                        }
                        var definition=catalog.findField(plan.request.getModality(),item,canonical).orElseThrow(()->new IllegalArgumentException("未知项目或字段代码"));
                        if(!plan.keys.add(item+"::"+canonical))throw new IllegalArgumentException("同一案例的测量字段重复");
                        boolean repeated=!rowNo.isBlank();
                        if(plan.values.stream().anyMatch(v->v.getItemCode().equals(item) && v.getFieldCode().matches("R[1-9][0-9]{0,2}__.+")!=repeated))throw new IllegalArgumentException("同一项目不可混用分行与普通字段");
                        if(raw.isBlank())throw new IllegalArgumentException("原始值不能为空");
                        var value=new RadiologyDto.MeasurementRequest();value.setItemCode(item);value.setFieldCode(definition.code());
                        try {var number=new BigDecimal(raw);if(number.precision()>38 || number.scale()>16)throw new IllegalArgumentException("数值超出支持精度");value.setValueNum(number);}catch(NumberFormatException ex){
                            if(!List.of("STP-MODEL","LCR-BASELINE-UNIT","UBD-QUALIFIER","ART-SOURCE","填“是”或“否”","是否有残影（如“否”无需填后两项）").contains(field))throw new IllegalArgumentException("此字段应为数值");value.setValueText(raw);
                        }
                        plan.values.add(value);
                        if(!data.get("CONDITION").isBlank()) {
                            String key=rowNo.isBlank()?item:item+".R"+rowNo;String old=plan.conditions.putIfAbsent(key,data.get("CONDITION"));
                            if(old!=null && !old.equals(data.get("CONDITION")))throw new IllegalArgumentException("同一测量行的检测条件不一致");
                            plan.conditions.putIfAbsent(item,data.get("CONDITION"));
                        }
                    }
                    statuses.add(new RowStatus(r+1,code,"READY","校验通过"));
                }catch(IllegalArgumentException ex){invalid=true;statuses.add(new RowStatus(r+1,code,"ERROR",ex.getMessage()));}
            }
        }catch(IOException ex){throw new BusinessException("Excel读取失败");}
        if(statuses.isEmpty())throw new BusinessException("工作簿没有数据");
        if(invalid || dryRun)return new Preview(dryRun,plans.size(),0,statuses);
        for(var plan:plans.values()) {
            var created=cases.createCase(plan.request);
            plan.ids.forEach((code,ids)->plan.conditions.put("__rows."+code,ids.toString()));
            var batch=new RadiologyDto.MeasurementBatchRequest();batch.setValues(plan.values);batch.setConditions(plan.conditions);batch.setExpectedDataVersion(created.dataVersion());cases.saveMeasurements(created.id(),batch);
        }
        return new Preview(false,plans.size(),plans.size(),statuses.stream().map(r->new RowStatus(r.row(),r.caseCode(),"IMPORTED","已导入")).toList());
    }
    private RadiologyDto.CaseRequest request(Map<String,String> d) {
        for(String key:HEADERS.subList(0,8))if(d.get(key).isBlank())throw new IllegalArgumentException("案例首次出现时须填写"+key);
        if(d.get("UNIT_NAME").length()>255 || d.get("EQUIPMENT_NAME").length()>255 || d.get("RECEIPT").length()>100)throw new IllegalArgumentException("单位、设备名称或受理编号过长");
        if(!List.of("CT","DR").contains(d.get("MODALITY")))throw new IllegalArgumentException("设备类别应为CT或DR");
        if(!List.of("验收检测","状态检测","稳定性检测").contains(d.get("CATEGORY")))throw new IllegalArgumentException("检测类别无效");
        if(d.get("MODALITY").equals("CT") && d.get("EQUIPMENT_NAME").toUpperCase(Locale.ROOT).contains("CBCT"))throw new IllegalArgumentException("牙科CBCT不适用普通CT规则");
        var r=new RadiologyDto.CaseRequest();r.setCaseCode(d.get("CASE_CODE"));r.setModality(RadiologyModality.valueOf(d.get("MODALITY")));r.setSampleAcceptanceNo(d.get("RECEIPT"));r.setInspectedUnitName(d.get("UNIT_NAME"));r.setEquipmentName(d.get("EQUIPMENT_NAME"));r.setDetectionCategory(d.get("CATEGORY"));
        try {r.setInspectionDate(LocalDate.parse(d.get("START_DATE")));r.setInspectionEndDate(LocalDate.parse(d.get("END_DATE")));}catch(java.time.DateTimeException ex){throw new IllegalArgumentException("日期请使用YYYY-MM-DD文本格式");}
        if(r.getInspectionEndDate().isBefore(r.getInspectionDate()))throw new IllegalArgumentException("结束日期早于开始日期");
        var profile=new RadiologyProfile();profile.setSelectedGroups(r.getModality()==RadiologyModality.CT?List.of("QC","PROTECTION"):List.of("GENERAL","SPECIAL","PROTECTION"));r.setProfile(profile);return r;
    }
    private void validateRepeatedMetadata(RadiologyDto.CaseRequest r,Map<String,String> d) {
        Map<String,String> original=Map.of("MODALITY",r.getModality().name(),"RECEIPT",r.getSampleAcceptanceNo(),"UNIT_NAME",r.getInspectedUnitName(),"EQUIPMENT_NAME",r.getEquipmentName(),"CATEGORY",r.getDetectionCategory(),"START_DATE",r.getInspectionDate().toString(),"END_DATE",r.getInspectionEndDate().toString());
        original.forEach((key,value)->{if(!d.get(key).isBlank() && !value.equals(d.get(key)))throw new IllegalArgumentException("同一案例的"+key+"不一致");});
    }
}
