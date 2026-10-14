package com.dsf.radiology.service;

import com.dsf.common.exception.BusinessException;
import com.dsf.radiology.entity.*;
import com.dsf.radiology.model.*;
import com.dsf.radiology.config.RadiologyStorageProperties;
import java.io.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** The same source document is used for draft preview and the frozen formal report. */
@Component
@RequiredArgsConstructor
public class RadiologyReportComposer {
    private final RadiologyCaseService cases;
    private final RadiologyCatalogService catalog;
    private final RadiologyStorageProperties storage;

    public byte[] compose(RadiologyCase inspection,List<RadiologyMeasurement> measurements,
            List<RadiologyProtectionPoint> points,List<RadiologyAttachment> attachments,boolean draft) {
        var profile=cases.profile(inspection);
        try(var doc=new XWPFDocument(new ClassPathResource(RadiologyReportService.templatePath(inspection.getModality())).getInputStream());
            var out=new ByteArrayOutputStream()) {
            String agency=StringUtils.hasText(inspection.getAgencyName())?inspection.getAgencyName():"北海市疾病预防控制中心（北海市卫生监督所）";
            Map<String,String> slots=new LinkedHashMap<>();
            slots.put("{{REPORT_NO}}",text(inspection.getSampleAcceptanceNo()));slots.put("{{AGENCY}}",agency);
            slots.put("{{SAMPLE}}",text(profile.getSampleName()==null?inspection.getEquipmentName():profile.getSampleName()));
            slots.put("{{UNIT}}",text(inspection.getInspectedUnitName()));slots.put("{{ADDRESS}}",text(inspection.getInspectedUnitAddress()));
            slots.put("{{SUBMITTING}}",text(profile.getSubmittingUnit()));slots.put("{{COMMISSIONED}}",text(profile.getCommissionedUnit()));
            slots.put("{{CATEGORY}}",text(inspection.getDetectionCategory()));
            slots.put("{{AGENCY_ADDRESS}}",agency.startsWith("北海市疾病")?"北海市海城区云南南路18号（邮编：536000）":"—");
            slots.put("{{AGENCY_PHONE}}",agency.startsWith("北海市疾病")?"0779-3902606，3909359":"—");
            for(var p:doc.getParagraphs()) replace(p,slots);
            for(var table:doc.getTables()) for(var row:table.getRows()) for(var cell:row.getTableCells()) for(var p:cell.getParagraphs()) replace(p,slots);
            for(var h:doc.getHeaderList()) for(var p:h.getParagraphs()) replace(p,slots);
            if(draft) paragraph(doc,"预览稿  当前状态："+inspection.getStatus()+"；未签发报告，仅供编制与复核使用。",true);
            basicInfo(doc,inspection,profile);
            var results=cases.itemResults(inspection);var conditions=cases.itemConditions(inspection);
            Map<String,Map<String,RadiologyMeasurement>> values=new LinkedHashMap<>();
            for(var m:measurements) values.computeIfAbsent(m.getItemCode(),k->new LinkedHashMap<>()).put(m.getFieldCode(),m);
            heading(doc,"检测项目及结果");
            if(inspection.getModality()==RadiologyModality.DR) {
                heading(doc,"一、X射线摄影设备通用检测项目");
                qualityTable(doc,inspection,results,conditions,values,false);
                heading(doc,"二、数字X射线摄影（DR）设备专用检测项目");
                qualityTable(doc,inspection,results,conditions,values,true);
            } else {heading(doc,"一、质量控制检测项目");qualityTable(doc,inspection,results,conditions,values,null);}
            var excluded=results.stream().filter(r->!r.itemCode().equals("RPD") && r.judgement()==RadiologyJudgement.NOT_APPLICABLE).map(r->r.itemName()+"："+r.ruleDescription()).distinct().toList();
            if(!excluded.isEmpty()) paragraph(doc,"注："+String.join("；",excluded),false);
            if(inspection.getModality()==RadiologyModality.CT && !RadiologyScopeService.type(inspection.getDetectionCategory()).equals("ACCEPTANCE") && cases.groupSelected(inspection,"CTDI")) paragraph(doc,"注：CT剂量指数的体部模体项目不检测；状态/稳定性检测仅检测头部模体。",false);
            if(cases.groupSelected(inspection,"RPD")) {
            page(doc);heading(doc,inspection.getModality()==RadiologyModality.DR?"三、放射工作场所卫生防护检测":"二、放射工作场所卫生防护检测");
            protectionTable(doc,inspection,profile,points,values);
            heading(doc,"检测点分布图");
            var map=attachments.stream().filter(a->"POINT_MAP".equals(a.getCategory())).findFirst();
            if(map.isPresent()) image(doc,map.get());else paragraph(doc,"未上传检测点分布图。",false);
            }
            page(doc);heading(doc,inspection.getModality()==RadiologyModality.CT?"三、检测结果评价":"四、检测结果评价");
            if(StringUtils.hasText(profile.getOverallConclusion())) paragraph(doc,profile.getOverallConclusion(),false);
            else {
                boolean qcFailure=results.stream().anyMatch(r->r.judgement()==RadiologyJudgement.UNQUALIFIED);
                boolean qcIncomplete=results.isEmpty() || results.stream().anyMatch(r->!r.complete() || r.judgement()==RadiologyJudgement.NOT_CONFIGURED);
                String target=text(inspection.getEquipmentModel())+"型"+text(inspection.getEquipmentName());
                String standard=inspection.getModality()==RadiologyModality.CT?"WS 519—2019":"WS 76—2020";
                paragraph(doc,"质量控制评价：本次对"+target+"进行"+text(inspection.getDetectionCategory())+"，"+
                    (!cases.groupSelected(inspection,inspection.getModality()==RadiologyModality.CT?"TPA":"TVD") && !cases.groupSelected(inspection,"HCR")?"本次委托未包含质量控制检测。":
                    qcIncomplete?"数据或判定资料尚未完整，不作最终评价。":qcFailure?"所检项目存在不符合项，详见结果表。":"所检可判定项目均符合"+standard+"规定的技术要求；建立基线项目单独记录。"),false);
                boolean failure=points.stream().anyMatch(p->p.getJudgement()==RadiologyJudgement.UNQUALIFIED);
                boolean incomplete=points.isEmpty() || points.stream().anyMatch(p->p.getJudgement()==RadiologyJudgement.INCOMPLETE || p.getJudgement()==RadiologyJudgement.NEEDS_ASSESSMENT);
                paragraph(doc,"场所防护评价："+(!cases.groupSelected(inspection,"RPD")?"本次委托未包含场所防护检测。":incomplete?"点位数据或年有效剂量评价尚未完整。":failure?"部分点位不符合控制要求，详见结果表。":"该放射工作场所所检点位的防护检测结果符合GBZ 130—2020相关控制要求。"),false);
            }
            if(StringUtils.hasText(inspection.getNotes())) paragraph(doc,"备注："+inspection.getNotes(),false);
            paragraph(doc,"编制人："+text(inspection.getWriterName())+"        复核人："+text(inspection.getReviewerName())+"        授权签字人："+text(inspection.getApproverName()),false);
            paragraph(doc,"签名：________________        ________________        ________________",false);
            paragraph(doc,"报告日期："+text(inspection.getReportDate())+"                         检验检测单位（章）",false);
            paragraph(doc,"（本报告结果仅对本次检验检测样品负责，以下空白）",false);
            doc.write(out);return out.toByteArray();
        }catch(Exception ex){if(ex instanceof BusinessException b)throw b;throw new BusinessException("报告排版失败："+ex.getMessage());}
    }

    private void basicInfo(XWPFDocument doc,RadiologyCase c,RadiologyProfile p) {
        XWPFTable t=doc.createTable(1,2);t.setWidth("100%");
        cell(t.getRow(0).getCell(0),"设备名称："+text(c.getEquipmentName())+"\n设备型号："+text(c.getEquipmentModel())+"\n设备编号："+text(c.getEquipmentNo())+"\n生产厂家："+text(c.getManufacturer())+"\n检测类别："+text(c.getDetectionCategory()),false);
        cell(t.getRow(0).getCell(1),"受检单位："+text(c.getInspectedUnitName())+"\n单位地址："+text(c.getInspectedUnitAddress())+"\n检测地点："+text(c.getDetectionLocation())+"\n检测时间："+text(c.getInspectionDate())+(c.getInspectionEndDate()!=null?" 至 "+c.getInspectionEndDate():"")+"\n检测人："+text(p.getDetectorName()),false);
        if(StringUtils.hasText(p.getEquipmentParameters()) || p.getManufacturedDate()!=null) paragraph(doc,"设备参数："+text(p.getEquipmentParameters())+"；出厂日期："+text(p.getManufacturedDate()),false);
        paragraph(doc,"检测项目："+(cases.groupSelected(c,c.getModality()==RadiologyModality.CT?"TPA":"TVD") || cases.groupSelected(c,"HCR")?"质量控制检测；":"")+(cases.groupSelected(c,"RPD")?"放射工作场所卫生防护检测":""),false);
        paragraph(doc,"检测依据："+text(c.getStandardBasis()),false);
        if(p.getInstruments()!=null && !p.getInstruments().isEmpty()) {
            int i=1;for(var instrument:p.getInstruments()) paragraph(doc,"检测仪器 "+i+++"："+text(instrument.getName())+"（型号："+text(instrument.getModel())+"；仪器编号："+text(instrument.getDeviceNo())+"）[证书编号："+text(instrument.getCertificateNo())+"]（有效期："+text(instrument.getValidFrom())+"至"+text(instrument.getValidTo())+"）",false);
        } else paragraph(doc,"检测仪器："+text(c.getInstrumentDescription()),false);
    }

    private void qualityTable(XWPFDocument doc,RadiologyCase c,List<com.dsf.radiology.dto.RadiologyDto.ItemCalculationResponse> results,
            Map<String,String> conditions,Map<String,Map<String,RadiologyMeasurement>> values,Boolean special) {
        var t=table(doc,"检测项目","检测条件","检测结果","标准要求","评价");
        Set<String> specific=Set.of("DDI","STP","RUE","REA","ILA","ART","HCR","LCR");
        for(var item:catalog.getCatalog(c.getModality())) {
            if(item.code().equals("RPD") || (special!=null && specific.contains(item.code())!=special))continue;
            var result=results.stream().filter(r->r.itemCode().equals(item.code())).findFirst().orElse(null);
            var fields=values.getOrDefault(item.code(),Map.of());
            String rule=result==null?"—":result.ruleDescription();
            String value=result==null?"待录入":result.resultText()!=null?result.resultText():format(fields.get("RESULT"));
            String judgement=result==null?"待录入":evaluation(result.judgement());
            String condition=conditions.getOrDefault(item.code(),"—");
            if(fields.containsKey("ROWS_JSON") && result!=null && result.judgement()!=RadiologyJudgement.NOT_APPLICABLE) {
                try {
                    var rows=new com.fasterxml.jackson.databind.ObjectMapper().readTree(fields.get("ROWS_JSON").getValueText());
                    for(var r:rows) row(t,item.name()+"（第"+r.path("rowId").asInt()+"行）",r.path("condition").asText("—"),r.path("result").asText("待录入"),
                        result.judgement()==RadiologyJudgement.NOT_CONFIGURED?rule:r.path("rule").asText(),
                        result.judgement()==RadiologyJudgement.NOT_CONFIGURED?"—":evaluation(RadiologyJudgement.valueOf(r.path("judgement").asText())));
                } catch(java.io.IOException ex) {throw new BusinessException("分行计算结果结构损坏，请重新计算");}
                continue;
            }
            boolean applicable=result!=null && result.complete() && result.judgement()!=RadiologyJudgement.NOT_APPLICABLE;
            List<String> groups=switch(item.code()) {
                case "TVD"->List.of("DISPLAY_1","DISPLAY_2","DISPLAY_3");case "ETD"->List.of("DEVIATION_MS_1","DEVIATION_MS_2","DEVIATION_MS_3");case "LRD"->List.of("RESULT_A1","RESULT_A2","RESULT_B1","RESULT_B2");
                case "REA"->List.of("HORIZONTAL","VERTICAL");case "TPA"->List.of("FORWARD","BACKWARD");case "CTDI"->List.of("HEAD_CTDIW","BODY_CTDIW");default->List.of();};
            if(groups.isEmpty() || !applicable) {row(t,item.name(),condition,value,rule,judgement);continue;}
            for(int index=0;index<groups.size();index++) {
                String code=groups.get(index);var field=fields.get(code);String label=switch(code){case "FORWARD"->"前进";case "BACKWARD"->"后退";case "HORIZONTAL"->"水平";case "VERTICAL"->"垂直";case "RESULT_A1"->"左a1";case "RESULT_A2"->"右a2";case "RESULT_B1"->"前b1";case "RESULT_B2"->"后b2";case "HEAD_CTDIW"->"头部模体";case "BODY_CTDIW"->"体部模体";default->"第"+(index+1)+"组";};
                String display=format(field), assessment=judgement, rowRule=rule;
                if(field==null) {display="不检测";assessment="—";rowRule=RadiologyScopeService.type(c.getDetectionCategory()).equals("ACCEPTANCE")?"本次未选择此部位":"WS 519—2019：状态/稳定性检测不检测体部模体";}
                else if(!rule.contains(" / ") && Set.of("LRD","REA","TPA").contains(item.code())) {
                    BigDecimal limit=new BigDecimal(item.code().equals("LRD")?"10":"2");assessment=field.getValueNum().abs().compareTo(limit)<=0?"符合":"不符合";
                    if(item.code().equals("LRD"))display=field.getValueNum().divide(BigDecimal.TEN).setScale(1,RoundingMode.HALF_EVEN)+" cm";
                } else if(!rule.contains(" / ") && item.code().equals("TVD")) {
                    var nominal=fields.get("TVD"+(index+1)+"-0");var deviation=fields.get("DEVIATION_KV_"+(index+1));
                    if(nominal!=null && deviation!=null) {
                        var limit=nominal.getValueNum().multiply(new BigDecimal("0.05")).max(new BigDecimal("5"));
                        assessment=deviation.getValueNum().abs().compareTo(limit)<=0?"符合":"不符合";
                        rule=nominal.getValueNum().compareTo(new BigDecimal("100"))<0 ? "WS 76—2020：偏离≤5 kV" : "WS 76—2020：偏离≤5%";
                    }
                } else if(!rule.contains(" / ") && item.code().equals("ETD")) {
                    var nominal=fields.get("ETD"+(index+1)+"-0");var relative=fields.get("DEVIATION_PERCENT_"+(index+1));
                    if(nominal!=null && nominal.getValueNum()!=null) {
                        BigDecimal n=nominal.getValueNum(),limit=n.compareTo(new BigDecimal("100"))>=0?n.multiply(new BigDecimal("0.10")):n.multiply(new BigDecimal("0.15")).max(new BigDecimal("2"));
                        assessment=field.getValueNum().abs().compareTo(limit)<=0?"符合":"不符合";
                        rowRule="WS 76—2020：|Δ|≤"+limit.stripTrailingZeros().toPlainString()+" ms";
                        if(relative!=null) display+="（"+format(relative)+"）";
                    }
                } else if(!rule.contains(" / ") && item.code().equals("CTDI")) {
                    String refPrefix=code.startsWith("HEAD")?"CTDI-HEAD-":"CTDI-BODY-";
                    var reference=fields.get(refPrefix+(RadiologyScopeService.type(c.getDetectionCategory()).equals("STABILITY")?"BASELINE":"MANUFACTURER"));
                    if(reference!=null && reference.getValueNum()!=null && reference.getValueNum().signum()>0) {
                        var deviation=field.getValueNum().subtract(reference.getValueNum()).divide(reference.getValueNum(),12,RoundingMode.HALF_EVEN).multiply(new BigDecimal("100"));
                        display=format(field)+"（偏差"+deviation.setScale(1,RoundingMode.HALF_EVEN)+"%）";assessment=deviation.abs().compareTo(new BigDecimal(RadiologyScopeService.type(c.getDetectionCategory()).equals("STATE")?"20":"15"))<=0?"符合":"不符合";
                    }
                }
                row(t,item.name()+"（"+label+"）",field==null?"—":conditions.getOrDefault(item.code()+"."+(index+1),condition)+"；"+label,display,item.code().equals("TVD")?rule:rowRule,assessment);
            }
        }
    }

    private void protectionTable(XWPFDocument doc,RadiologyCase c,RadiologyProfile p,List<RadiologyProtectionPoint> points,Map<String,Map<String,RadiologyMeasurement>> values) {
        var t=table(doc,"序号","检测点位置","检测结果（μSv/h）","控制目标值（μSv/h）","评价");
        int i=1;for(var point:points)row(t,String.valueOf(i++),text(point.getPointName())+" "+text(point.getLocationDescription()),number(point.getResultValue(),3),"≤"+number(point.getControlLimit(),1),evaluation(point.getJudgement()));
        if(points.isEmpty())row(t,"—","—","—","—","—");
        var rpd=values.getOrDefault("RPD",Map.of());
        String calibration=switch(p.getCalibrationMode()==null?"INSTRUMENT":p.getCalibrationMode()){case "NONE"->"无需修正";case "TIME"->"仅时间响应修正";case "BOTH"->"仪器刻度与时间响应修正";default->"仅仪器刻度修正";};
        paragraph(doc,"注：1.检测条件："+text(p.getProtectionConditions())+"；仪器校准因子k1："+format(rpd.get("k"))+"；修正方式："+calibration+"；仪器响应时间："+text(p.getInstrumentResponseTime())+"；时间响应修正因子k2："+format(rpd.get("RPD-k2"))+"。",false);
        var background=rpd.get("RESULT_TEXT");
        paragraph(doc,"2.工作场所环境本底值："+(background==null?"—":format(background))+" μSv/h；本底测量运行情况："+text(p.getBackgroundOperation())+"；检测地点："+text(p.getBackgroundLocation())+"。",false);
        paragraph(doc,"3.检测结果"+("UNSUBTRACTED".equals(c.getProtectionMode())?"未扣除":"已扣除")+"本底值。",false);
        for(var point:points) if(point.getAnnualEffectiveDose()!=null && point.getResultValue()!=null && point.getResultValue().compareTo(new BigDecimal("25"))>0)
            paragraph(doc,"年有效剂量评价："+text(point.getPointName())+"；工作负荷"+number(point.getAnnualWorkloadMas(),3)+" mAs/a，居留因子"+number(point.getOccupancyFactor(),3)+"，使用因子"+number(point.getUseFactor(),3)+"；年有效剂量"+number(point.getAnnualEffectiveDose(),4)+" mSv/a；控制值≤0.25 mSv/a。",false);
    }

    private void image(XWPFDocument doc,RadiologyAttachment a) throws Exception {
        Path file=Path.of(a.getStoragePath()).toAbsolutePath().normalize(),root=Path.of(storage.getBaseDir()).toAbsolutePath().normalize();
        if(!file.startsWith(root) || !Files.isRegularFile(file))throw new BusinessException("点位图文件缺失或路径无效");
        var buffered=javax.imageio.ImageIO.read(file.toFile());if(buffered==null)throw new BusinessException("无法读取点位图");
        double width=Math.min(540,buffered.getWidth()),height=width*buffered.getHeight()/buffered.getWidth();
        if(height>350){width*=350/height;height=350;}
        var png=new ByteArrayOutputStream();javax.imageio.ImageIO.write(buffered,"png",png);
        var p=doc.createParagraph();p.setAlignment(ParagraphAlignment.CENTER);
        p.createRun().addPicture(new ByteArrayInputStream(png.toByteArray()),Document.PICTURE_TYPE_PNG,a.getOriginalName(),Units.toEMU(width),Units.toEMU(height));
    }
    private void replace(XWPFParagraph p,Map<String,String> slots) {
        for(var run:p.getRuns()){String value=run.getText(0);if(value!=null){for(var slot:slots.entrySet())value=value.replace(slot.getKey(),slot.getValue());run.setText(value,0);}}
    }
    private XWPFTable table(XWPFDocument doc,String... headers) {
        var t=doc.createTable(1,headers.length);t.setWidth("100%");t.setTopBorder(XWPFTable.XWPFBorderType.SINGLE,4,0,"000000");t.setBottomBorder(XWPFTable.XWPFBorderType.SINGLE,4,0,"000000");t.setInsideHBorder(XWPFTable.XWPFBorderType.SINGLE,4,0,"000000");t.setInsideVBorder(XWPFTable.XWPFBorderType.SINGLE,4,0,"000000");
        if(headers.length==5 && headers[0].equals("检测项目")) {
            int[] widths={1900,2500,1700,2700,950};
            var grid=t.getCTTbl().getTblGrid()!=null?t.getCTTbl().getTblGrid():t.getCTTbl().addNewTblGrid();
            while(grid.sizeOfGridColArray()>0)grid.removeGridCol(0);
            for(int width:widths)grid.addNewGridCol().setW(java.math.BigInteger.valueOf(width));
            var pr=t.getCTTbl().getTblPr();pr.addNewTblLayout().setType(org.openxmlformats.schemas.wordprocessingml.x2006.main.STTblLayoutType.FIXED);
            for(int i=0;i<widths.length;i++)t.getRow(0).getCell(i).setWidth(String.valueOf(widths[i]));
        }
        t.getRow(0).setRepeatHeader(true);for(int i=0;i<headers.length;i++)cell(t.getRow(0).getCell(i),headers[i],true);return t;
    }
    private void row(XWPFTable t,String... values){var r=t.createRow();boolean failure=Arrays.asList(values).contains("不符合");r.setCantSplitRow(true);for(int i=0;i<values.length;i++)cell(r.getCell(i),values[i],failure);}
    private void cell(XWPFTableCell c,String value,boolean bold){c.setText("");c.setVerticalAlignment(XWPFTableCell.XWPFVertAlign.CENTER);var p=c.getParagraphs().get(0);p.setSpacingAfter(0);p.setSpacingBefore(0);p.setAlignment(ParagraphAlignment.LEFT);write(p,text(value),10,bold);}
    private void heading(XWPFDocument doc,String value){var p=doc.createParagraph();p.getCTP().addNewPPr();p.setKeepNext(true);p.setSpacingBefore(100);p.setSpacingAfter(60);write(p,value,11,true);}
    private void paragraph(XWPFDocument doc,String value,boolean bold){var p=doc.createParagraph();p.setSpacingAfter(50);write(p,value,10,bold);}
    private void write(XWPFParagraph p,String value,int size,boolean bold){var r=p.createRun();String[] lines=value.split("\\n",-1);for(int i=0;i<lines.length;i++){if(i>0)r.addBreak();r.setText(lines[i]);}r.setFontFamily("宋体");r.setFontSize(size);r.setBold(bold);r.setColor("000000");r.getCTR().addNewRPr().addNewRFonts().setEastAsia("宋体");}
    private void page(XWPFDocument doc){doc.createParagraph().createRun().addBreak(BreakType.PAGE);}
    private String format(RadiologyMeasurement m){if(m==null)return "—";if(m.getFieldRole()==RadiologyFieldRole.RAW && StringUtils.hasText(m.getValueText()))return m.getValueText();if(m.getValueNum()==null)return text(m.getValueText());return number(m.getValueNum(),m.getDisplayScale()==null?3:m.getDisplayScale())+(StringUtils.hasText(m.getUnit())?" "+m.getUnit():"");}
    private String number(BigDecimal n,int scale){return n==null?"—":n.setScale(scale,RoundingMode.HALF_EVEN).toPlainString();}
    private String text(Object value){return value==null || value.toString().isBlank()?"—":value.toString();}
    private String evaluation(RadiologyJudgement j){if(j==null)return "待录入";return switch(j){case QUALIFIED->"符合";case UNQUALIFIED->"不符合";case NOT_APPLICABLE,BASELINE_ESTABLISHED->"—";case INCOMPLETE->"数据不完整";case NOT_CONFIGURED->"待补资料";case NEEDS_ASSESSMENT->"需进一步评估";};}
}
