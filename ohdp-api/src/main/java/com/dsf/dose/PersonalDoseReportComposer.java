package com.dsf.dose;

import com.dsf.common.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import java.io.*;
import java.util.*;
import static com.dsf.dose.PersonalDoseCalculator.text;

@Component
public class PersonalDoseReportComposer {
    public byte[] compose(JsonNode d,boolean draft){
        try(var doc=new XWPFDocument(new ClassPathResource("templates/radiology/PERSONAL-DOSE-v20261014.docx").getInputStream());var out=new ByteArrayOutputStream()){
            Map<String,String> slots=new LinkedHashMap<>();
            slots.put("{{REPORT_NO}}",text(d,"receipt"));slots.put("{{AGENCY}}","北海市疾病预防控制中心（北海市卫生监督所）");slots.put("{{SAMPLE}}",text(d,"sampleName"));slots.put("{{UNIT}}",text(d,"unitName"));slots.put("{{ADDRESS}}",text(d,"unitAddress"));slots.put("{{SUBMITTING}}",text(d,"submittingUnit"));slots.put("{{COMMISSIONED}}",text(d,"commissioningUnit"));slots.put("{{CATEGORY}}",text(d,"detectionCategory"));slots.put("{{AGENCY_ADDRESS}}","北海市海城区云南南路18号（邮编：536000）");slots.put("{{AGENCY_PHONE}}","0779-3902606，3909359");
            for(var p:doc.getParagraphs())replace(p,slots);for(var t:doc.getTables())for(var r:t.getRows())for(var c:r.getTableCells())for(var p:c.getParagraphs())replace(p,slots);for(var h:doc.getHeaderList())for(var p:h.getParagraphs())replace(p,slots);
            if(draft)p(doc,"预览稿——仅供编制与复核，未经批准及线下签章。",true);
            var info=table(doc,"样品信息","受检信息");row(info,"样品名称："+text(d,"sampleName")+"\n样品包装："+text(d,"packaging")+"\n检测类别："+text(d,"detectionCategory")+"\n样品性状："+text(d,"sampleCondition")+"\n检测项目："+text(d,"monitoringItem")+"\n收检日期："+text(d,"receivedDate"),"受检单位："+text(d,"unitName")+"\n送检单位："+text(d,"submittingUnit")+"\n委托单位："+text(d,"commissioningUnit")+"\n受检人数："+d.path("personnel").size()+"人\n监测起止日期："+text(d,"monitoringStart")+" 至 "+text(d,"monitoringEnd")+"（第"+text(d,"cycle")+"周期）");
            p(doc,"探测器："+text(d,"detectorType"),false);p(doc,"检测仪器："+text(d,"instrumentDescription"),false);p(doc,"检测依据："+text(d,"standardBasis"),false);
            p(doc,"一、检验检测项目及结果",true);
            var t=table(doc,"剂量计编号","姓名","性别","职业类别","剂量计佩戴起止日期","佩戴时间（天）","个人剂量当量（mSv）Hp(10)");
            Map<String,JsonNode> results=new HashMap<>();for(JsonNode r:d.path("calculation").path("rows"))if(text(r,"sampleType").equals("TEST"))results.put(text(r,"dosimeterNo"),r);
            for(JsonNode person:d.path("personnel")){JsonNode r=results.get(text(person,"dosimeterNo"));String dose=r==null?"待计算":text(r,"hp10");if(r!=null && r.path("belowMdl").asBoolean())dose+="（低于MDL）";if(r!=null && r.path("investigationRequired").asBoolean())dose+="※";row(t,text(person,"dosimeterNo"),text(person,"name"),text(person,"sex"),text(person,"occupationCategory"),text(person,"startDate")+" 至 "+text(person,"endDate"),text(person,"wearingDays"),dose);}
            if(d.path("personnel").isEmpty())row(t,"—","—","—","—","—","—","待录入");
            p(doc,"注：1.仪器的最低可探测水平（MDL）为 "+text(d,"mdl")+" mSv。低于MDL的计算结果已标注。",false);
            p(doc,"2.本周期剂量调查水平参考值为 "+text(d,"investigationLevel")+" mSv，凡检测结果≥ "+text(d,"investigationLevel")+" mSv的均需调查（※）。",false);
            p(doc,"二、结果评价",true);p(doc,text(d,"conclusion").isBlank()?"待编制人员结合本周期检测结果填写。单个周期不能代替年度累计剂量评价。":text(d,"conclusion"),false);
            var signatures=table(doc,"编制人：\n________________","复核人：\n________________","授权签字人：\n________________");
            signatures.setTopBorder(XWPFTable.XWPFBorderType.NONE,0,0,"FFFFFF");signatures.setBottomBorder(XWPFTable.XWPFBorderType.NONE,0,0,"FFFFFF");signatures.setLeftBorder(XWPFTable.XWPFBorderType.NONE,0,0,"FFFFFF");signatures.setRightBorder(XWPFTable.XWPFBorderType.NONE,0,0,"FFFFFF");signatures.setInsideHBorder(XWPFTable.XWPFBorderType.NONE,0,0,"FFFFFF");signatures.setInsideVBorder(XWPFTable.XWPFBorderType.NONE,0,0,"FFFFFF");
            p(doc,"报告日期："+text(d,"reportDate")+"                 检验检测单位（章）",false);p(doc,"（本报告结果仅对本次检验检测样品负责，以下空白）",false);
            doc.write(out);return out.toByteArray();
        }catch(Exception e){throw new BusinessException("个人剂量报告生成失败："+e.getMessage());}
    }
    private void replace(XWPFParagraph p,Map<String,String> slots){for(var run:p.getRuns()){String s=run.getText(0);if(s!=null){for(var e:slots.entrySet())s=s.replace(e.getKey(),e.getValue());run.setText(s,0);}}}
    private void p(XWPFDocument d,String s,boolean bold){var p=d.createParagraph();layout(p);p.setSpacingAfter(80);if(bold)p.setKeepNext(true);write(p,s,11,bold);}
    private XWPFTable table(XWPFDocument d,String... headers){var t=d.createTable(1,headers.length);t.setWidth("100%");
        int[] widths=switch(headers.length){case 7->new int[]{1100,1100,600,1100,2600,1100,2350};case 2->new int[]{4500,5450};default->new int[]{3316,3317,3317};};
        var grid=t.getCTTbl().getTblGrid()!=null?t.getCTTbl().getTblGrid():t.getCTTbl().addNewTblGrid();while(grid.sizeOfGridColArray()>0)grid.removeGridCol(0);for(int width:widths)grid.addNewGridCol().setW(java.math.BigInteger.valueOf(width));t.getCTTbl().getTblPr().addNewTblLayout().setType(org.openxmlformats.schemas.wordprocessingml.x2006.main.STTblLayoutType.FIXED);
        t.getRow(0).setRepeatHeader(true);for(int i=0;i<headers.length;i++){t.getRow(0).getCell(i).setWidth(String.valueOf(widths[i]));cell(t.getRow(0).getCell(i),headers[i],true);}return t;}
    private void row(XWPFTable t,String... values){var r=t.createRow();r.setCantSplitRow(true);for(int i=0;i<values.length;i++)cell(r.getCell(i),values[i],false);}
    private void cell(XWPFTableCell c,String s,boolean bold){c.setText("");var p=c.getParagraphs().get(0);layout(p);p.setSpacingAfter(40);p.setSpacingBefore(40);write(p,s,10,bold);}
    private void layout(XWPFParagraph p){p.setAlignment(ParagraphAlignment.LEFT);p.setSpacingBefore(0);p.setSpacingBetween(16,LineSpacingRule.EXACT);var pr=p.getCTP().isSetPPr()?p.getCTP().getPPr():p.getCTP().addNewPPr();if(pr.isSetSnapToGrid())pr.getSnapToGrid().setVal(false);else pr.addNewSnapToGrid().setVal(false);}
    private void write(XWPFParagraph p,String s,int size,boolean bold){String[] lines=(s.isBlank()?"—":s).split("\\n");for(int i=0;i<lines.length;i++){var run=p.createRun();run.setFontFamily("宋体");run.setFontSize(size);run.setBold(bold);if(i>0)run.addBreak();run.setText(lines[i]);}}
}
