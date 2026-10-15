package com.dsf.dose;

import com.dsf.common.exception.BusinessException;
import com.fasterxml.jackson.databind.node.*;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.io.*;
import java.util.*;
import static com.dsf.dose.PersonalDoseCalculator.require;

@Service @RequiredArgsConstructor
public class PersonalDoseImportService {
    private final PersonalDoseService service;
    private final PersonalDoseCalculator calculator;
    private static final Map<String,List<String>> KEYS=Map.of("cases",List.of("receipt","unitName","detectionCategory","unitAddress","commissioningUnit","sampleName","monitoringMonths","sampleCount","packaging","monitoringItem","monitoredQuantity","monitoringStart","monitoringEnd","cycle","standardBasis","inspectionDate","detector","instrumentDescription","mdl","investigationLevel","submittingUnit","location","city","county","sampleCondition","distributionStart","distributionEnd","receivedDate","reportDate","detectorType","conclusion"),"personnel",List.of("dosimeterNo","name","sex","occupationCategory","job","startDate","endDate","wearingPosition"),"readings",List.of("sampleType","dosimeterNo","name","values","cf"));
    private static final Map<String,List<String>> LABELS=Map.of("cases",List.of("受理编号","受检单位","检测类别","单位地址","委托单位","样品名称","监测周期（月）","样品数量","样品包装","监测项目","监测量","监测开始日期","监测结束日期","第几周期","检测依据","检测日期","检测人","检测仪器及证书","MDL（mSv）","调查水平（mSv）","送检单位","检测地点","所在地市","所在县区","样品性状","剂量计发放开始日期","剂量计发放结束日期","收检日期","报告日期","探测器类型","总体结果评价"),"personnel",List.of("剂量计编号","姓名","性别","职业类别","岗位","佩戴起始日期","佩戴结束日期","佩戴位置"),"readings",List.of("样品类别","剂量计编号","姓名","原始测量值（mSv；分号分隔）","刻度因子Cf"));
    private void kind(String k){require(KEYS.containsKey(k),"未知导入类型");}
    public byte[] template(String kind){kind(kind);try(var wb=new XSSFWorkbook();var out=new ByteArrayOutputStream()){var s=wb.createSheet("数据");var r=s.createRow(0);for(int i=0;i<LABELS.get(kind).size();i++){r.createCell(i).setCellValue(LABELS.get(kind).get(i));s.setColumnWidth(i,6000);s.setDefaultColumnStyle(i,textStyle(wb));}s.createFreezePane(0,1);var note=wb.createSheet("说明");note.createRow(0).createCell(0).setCellValue("请保持表头。日期用YYYY-MM-DD文本。样品类别填质控剂量计、本底剂量计、待测剂量计；测量值以英文分号分隔并保留有效位数。人员/测量导入将替换该项目对应列表，请先预检。");wb.write(out);return out.toByteArray();}catch(IOException ex){throw new BusinessException("导入模板生成失败");}}
    private CellStyle textStyle(Workbook wb){var style=wb.createCellStyle();style.setDataFormat(wb.createDataFormat().getFormat("@"));return style;}
    @Transactional
    public ObjectNode importFile(String kind,Long id,Long version,MultipartFile file,boolean dryRun){
        kind(kind);require(file.getSize()>0 && file.getSize()<=5*1024*1024,"请上传不超过5MB的xlsx文件");ArrayNode rows=JsonNodeFactory.instance.arrayNode();
        try(var wb=new XSSFWorkbook(file.getInputStream())){var sheet=wb.getSheetAt(0);require(sheet.getLastRowNum()<=2000,"单次导入最多2000行");DataFormatter f=new DataFormatter();var header=sheet.getRow(0);require(header!=null,"缺少表头");int columns=KEYS.get(kind).size();if(kind.equals("cases") && header.getLastCellNum()==20)columns=20;for(int i=0;i<columns;i++){String actual=f.formatCellValue(header.getCell(i)).trim();boolean legacy=kind.equals("readings")&&i==3&&actual.equals("原始测量值（分号分隔）");require(legacy||LABELS.get(kind).get(i).equals(actual),"表头不匹配，请使用本系统模板");}
            for(int n=1;n<=sheet.getLastRowNum();n++){var row=sheet.getRow(n);if(row==null)continue;ObjectNode data=JsonNodeFactory.instance.objectNode();boolean any=false;for(int i=0;i<columns;i++){var cell=row.getCell(i);require(cell==null || cell.getCellType()!=CellType.FORMULA,"第"+(n+1)+"行含公式，请粘贴为文本/数值");String s=f.formatCellValue(cell).trim();if(!s.isBlank())any=true;data.put(KEYS.get(kind).get(i),s);}if(!any)continue;
                if(kind.equals("readings")){String type=data.path("sampleType").asText();data.put("sampleType",switch(type){case "待测剂量计"->"TEST";case "本底剂量计"->"BACKGROUND";case "质控剂量计"->"QC";default->type;});ArrayNode values=data.putArray("values");String original=f.formatCellValue(row.getCell(3));for(String v:original.split("[;；]",-1))values.add(v.trim());}
                if(kind.equals("cases")){data.putArray("personnel");data.putArray("readings");calculator.validate(data,false);}rows.add(data);
            }
        }catch(BusinessException e){throw e;}catch(Exception e){throw new BusinessException("Excel解析失败，请使用下载的xlsx模板");}
        require(!rows.isEmpty(),"导入文件没有数据");ObjectNode response=JsonNodeFactory.instance.objectNode();response.put("dryRun",dryRun);response.put("count",rows.size());
        if(kind.equals("cases")){require(rows.size()<=200,"单次最多创建200个项目");Set<String> receipts=new HashSet<>();for(var row:rows)require(receipts.add(row.path("receipt").asText()),"导入表内受理编号重复");if(!dryRun){var ids=response.putArray("ids");for(var row:rows)ids.add(service.save(null,(ObjectNode)row).path("id").asLong());}}
        else {require(id!=null && version!=null,"请选择项目并保存当前编辑");ObjectNode d=service.get(id);require(d.path("version").asLong()==version,"项目已更新，请刷新");require(Set.of("DRAFT","DATA_ENTERED","CALCULATED").contains(d.path("status").asText()),"审核中或归档项目不能导入");d.set(kind,rows);calculator.validate(d,false);if(!dryRun)response.set("project",service.save(id,d));}
        return response;
    }
}
