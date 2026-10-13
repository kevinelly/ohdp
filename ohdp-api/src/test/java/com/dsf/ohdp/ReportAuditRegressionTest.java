package com.dsf.ohdp;
import com.dsf.ohdp.support.BeiHaiPeriodicReportGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;
import java.util.*;
import java.io.*;
import java.nio.file.*;
import org.apache.poi.xwpf.usermodel.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
class ReportAuditRegressionTest {
 private static final ObjectMapper JSON=new ObjectMapper();
 private static String json(Object value) throws Exception {return JSON.writeValueAsString(value);}
 Map<String,String> fixture() throws Exception {
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("__PROJECT_JSON__", json(Map.ofEntries(
                Map.entry("projectCode", "职20260902-001"),
                Map.entry("companyName", "北海市示范机械制造有限公司"),
                Map.entry("companyAddress", "北海市海城区示范路18号"),
                Map.entry("sampleName", "工作场所空气等"),
                Map.entry("submitUnit", "/"),
                Map.entry("entrustUnit", "北海市示范机械制造有限公司"),
                Map.entry("detectType", "定期检测"),
                Map.entry("statementDate", "2026年9月2日"),
                Map.entry("reportDate", "2026年9月2日"),
                Map.entry("orgName", "北海市疾病预防控制中心（北海市卫生监督所）"),
                Map.entry("taskOrigin", "受检单位委托开展职业病危害因素定期检测。"),
                Map.entry("evaluationBasis", "GBZ 159—2004、GBZ 2.1—2019"),
                Map.entry("writerName", "张检测"),
                Map.entry("reviewerName", "李审核"),
                Map.entry("approverName", "王签发"))));
        payload.put("__REPORT_SUPPLEMENT_JSON__", json(Map.ofEntries(
                Map.entry("reportHeaderName", "广西北海市示范机械装备制造技术服务有限责任公司"),
                Map.entry("socialCreditCode", "91450502TEST000001"),
                Map.entry("legalRepresentative", "陈海"),
                Map.entry("registeredAddress", "北海市海城区示范路18号"),
                Map.entry("technicalServiceAddress", "北海市海城区示范路18号"),
                Map.entry("contactName", "黄工"),
                Map.entry("contactPhone", "0779-1234567"),
                Map.entry("industryCategory", "制造业/通用设备制造业/金属加工机械制造"),
                Map.entry("lawsRegulations", "《中华人民共和国职业病防治法》、《工作场所职业卫生管理规定》"),
                Map.entry("basicMaterials", "用人单位提供的委托书、现场调查资料和检测计划。"),
                Map.entry("detectionScope", "焊接车间正常生产状态下的危害因素。"),
                Map.entry("productionProcess", "钢材经焊接、打磨和装配形成产品。"),
                Map.entry("radiationSourceMode", "INCLUDED_REPORT"),
                Map.entry("radiationSourceSummary", "用人单位存在射线装置，本次采用第③种方式并纳入本报告。"),
                Map.entry("vocAnalysisConclusion", "本项目已开展物料挥发性有机组分定性分析。"),
                Map.entry("vocSamplingDate", "2026年9月1日"),
                Map.entry("vocDetectionDate", "2026年9月2日"),
                Map.entry("vocDetectionBasis", "年度监测技术方案顶空气相色谱-质谱法"),
                Map.entry("vocDetectionMethod", "顶空气相色谱-质谱法"),
                Map.entry("vocInstrument", "GC-MS-01"),
                Map.entry("vocTotalPeakAreaPercent", "96.5"),
                Map.entry("vocInorganicPeakAreaPercent", "3.5"),
                Map.entry("exclusionScreeningPerformed", true),
                Map.entry("exclusionScreeningConclusion", "已开展排除性检测，确认不接触丙酮。"),
                Map.entry("exclusionScreeningItems", List.of(Map.of(
                        "workplace", "喷漆房", "jobName", "喷漆工", "factorName", "丙酮",
                        "ctwa", "0.20", "pcTwa", "300", "contactJudgement", "排除职业接触"))),
                Map.entry("productionCondition", "检测期间生产设备正常运行。"),
                Map.entry("samplingLayoutDescription", "按岗位设置代表性采样点。"),
                Map.entry("detectionConditions", "现场检测条件符合要求。"),
                Map.entry("environmentConditions", "温度28℃，湿度70%，气压100.6kPa"),
                Map.entry("onsiteInspectors", "张检测、赵采样"),
                Map.entry("planDeviation", "现场检测计划无变更。"),
                Map.entry("riskCategory", "严重"),
                Map.entry("overLimitSummary", "本次检测未发现超标岗位。"),
                Map.entry("majorProblems", "局部排风罩距离产尘点较远。"),
                Map.entry("recommendations", "调整局部排风罩位置并加强维护。"),
                Map.entry("qualificationCertificateImageDataUrl", "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAusB9Y9ZdeIAAAAASUVORK5CYII="),
                Map.entry("qualificationCertificateFileName", "certificate.png"),
                Map.entry("reportPhotos", List.of(Map.of(
                        "fileName", "onsite.png", "caption", "现场检测", "takenDate", "2026-09-01",
                        "imageDataUrl", "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAusB9Y9ZdeIAAAAASUVORK5CYII="))),
                Map.entry("materials", List.of(Map.of(
                        "workplace", "焊接车间", "materialName", "实芯焊丝", "processStage", "焊接",
                        "mainComponents", "铁、锰", "annualAmount", "12t/a", "physicalState", "固态", "positions", "焊工"))),
                Map.entry("vocAnalysisPerformed", true),
                Map.entry("vocAnalysisItems", List.of(Map.of(
                        "chemicalName", "示范稀释剂", "manufacturerBatchNo", "示范厂家/20260901",
                        "detectedComponents", "二甲苯", "casNo", "1330-20-7", "peakAreaPercent", "68.2"))),
                Map.entry("xrayDevices", List.of(Map.ofEntries(
                        Map.entry("serialNo", "1"), Map.entry("workplace", "无损检测室"),
                        Map.entry("deviceName", "工业X射线探伤机"), Map.entry("category", "Ⅱ类射线装置"),
                        Map.entry("model", "XXQ-2505"), Map.entry("manufacturer", "示范射线设备厂"),
                        Map.entry("mainParameters", "250 kV/5 mA"), Map.entry("radiationType", "X射线")))),
                Map.entry("sealedSources", List.of()), Map.entry("unsealedSources", List.of()),
                Map.entry("equipment", List.of()),
                Map.entry("protectionFacilities", List.of()),
                Map.entry("personalProtectiveEquipment", List.of()),
                Map.entry("samplingPlans", List.of(Map.ofEntries(
                        Map.entry("workplace", "焊接车间"), Map.entry("jobName", "焊工"),
                        Map.entry("operationType", "定岗"), Map.entry("workersPerShift", 6),
                        Map.entry("targetLocationTiming", "正常焊接时"), Map.entry("factorName", "锰及其无机化合物"),
                        Map.entry("limitType", "PC-TWA"), Map.entry("contactType", "相对稳定"),
                        Map.entry("detectionMode", "个体"), Map.entry("detectionTimeType", "长时间检测"),
                        Map.entry("pointsPerDay", 2), Map.entry("timesPerDay", 1), Map.entry("days", 3)))))));
        payload.put("__POSITIONS_JSON__", json(List.of(Map.ofEntries(
                Map.entry("workLocation", "焊接车间"), Map.entry("jobName", "焊工"),
                Map.entry("shiftSystem", "单班制"), Map.entry("workingHours", "8h/d，5d/w"),
                Map.entry("workerCount", 6), Map.entry("operationType", "定岗"),
                Map.entry("operationContent", "工件焊接"), Map.entry("hazards", "焊接烟尘、锰及其无机化合物、噪声"),
                Map.entry("operationDuration", "6h/d")))));
        payload.put("__RESULTS_JSON__", json(List.of(
                Map.ofEntries(Map.entry("workLocation", "焊接车间"), Map.entry("jobName", "焊工"),
                        Map.entry("factorName", "锰及其无机化合物"), Map.entry("metricType", "TWA"),
                        Map.entry("valueNum", 0.062), Map.entry("unit", "mg/m3"), Map.entry("limitValue", 0.15),
                        Map.entry("limitUnit", "mg/m3"), Map.entry("judgement", "合格")),
                Map.ofEntries(Map.entry("workLocation", "焊接车间"), Map.entry("jobName", "焊工"),
                        Map.entry("factorName", "噪声"), Map.entry("metricType", "LEX8H"),
                        Map.entry("valueNum", 82.6), Map.entry("unit", "dB(A)"), Map.entry("limitValue", 85),
                        Map.entry("limitUnit", "dB(A)"), Map.entry("judgement", "合格")),
                Map.ofEntries(Map.entry("workLocation", "喷漆房"), Map.entry("jobName", "喷漆工"),
                        Map.entry("factorName", "二甲苯"), Map.entry("metricType", "CPE"),
                        Map.entry("valueNum", 18.0), Map.entry("unit", "mg/m3"), Map.entry("limitValue", 50),
                        Map.entry("limitUnit", "mg/m3"), Map.entry("judgement", "合格")),
                Map.ofEntries(Map.entry("workLocation", "无损检测室"), Map.entry("jobName", "探伤工"),
                        Map.entry("factorName", "X、γ射线"), Map.entry("metricType", "X_GAMMA"),
                        Map.entry("valueNum", 0.12), Map.entry("unit", "μSv/h"), Map.entry("limitValue", 2.5),
                        Map.entry("limitUnit", "μSv/h"), Map.entry("judgement", "合格")))));
        payload.put("__STAFF_JSON__", json(Map.of(
                "writer", Map.of("name", "张检测", "title", "主管技师", "profession", "职业卫生"),
                "reviewer", Map.of("name", "李审核", "title", "副主任技师", "profession", "职业卫生"),
                "approver", Map.of("name", "王签发", "title", "主任技师", "profession", "公共卫生"))));
        payload.put("__METHODS_JSON__", json(Map.of(
                "chemical", List.of(Map.of(
                        "factorName", "锰及其无机化合物", "basis", "GBZ/T 160.13—2004",
                        "method", "原子吸收光谱法", "instruments", "原子吸收分光光度计 AAS-01")),
                "physical", List.of(Map.of(
                        "factorName", "噪声", "basis", "GBZ/T 189.8—2007",
                        "method", "仪器直读法", "instruments", "个体噪声剂量计 NS-01")))));


        ObjectNode project=(ObjectNode)JSON.readTree(payload.get("__PROJECT_JSON__"));
        project.put("orgName","验收测试机构（非正式报告）");project.put("projectCode","职20260917-全面整改验收-001");project.put("reportDate","2026-09-17");
        payload.put("__PROJECT_JSON__",json(project));
        ObjectNode supplement=(ObjectNode)JSON.readTree(payload.get("__REPORT_SUPPLEMENT_JSON__"));
        supplement.put("qualificationCertificateImageDataUrl","data:image/png;base64,"+Base64.getEncoder().encodeToString(getClass().getResourceAsStream("/report-audit/certificate.png").readAllBytes()));
        supplement.set("reportPhotos",JSON.valueToTree(List.of(Map.of("fileName","photo.png","caption","测试现场照片，非真实现场","takenDate","2026-09-11","imageDataUrl","data:image/png;base64,"+Base64.getEncoder().encodeToString(getClass().getResourceAsStream("/report-audit/photo.png").readAllBytes())))));
        supplement.put("vocAnalysisPerformed",false);supplement.put("vocAnalysisConclusion","本项目未开展VOC定性分析。");supplement.put("employeeCount",18);
        supplement.put("mixedExposureAssessment","NOT_APPLICABLE");supplement.put("mixedExposureAssessmentBasis","本验收样例不进行同效应相加评价。");
        supplement.put("commissioningDate","2020-01-01");supplement.put("economicType","有限责任公司");supplement.put("companyScale","小型");
        supplement.put("radiationSourceSummary","本验收样例合并放射性检测数据；检测主体见结果单。");
        supplement.put("planDeviation","本验收样例实际检测日期为2026-09-10、2026-09-11；计划已对应调整为两日。");
        supplement.set("personalProtectiveEquipment",JSON.valueToTree(List.of(Map.of("category","呼吸防护","ppeName","防尘口罩","manufacturer","测试厂家甲","model","KN95","protectionParameters","颗粒物过滤","positions","焊工","usageStatus","正常使用"))));
        payload.put("__REPORT_SUPPLEMENT_JSON__",json(supplement));
        List<Map<String,Object>> rows=new ArrayList<>();
        rows.add(row("二甲苯","TWA","45","50","mg/m3","P-CHEM","2026-09-10"));
        rows.get(0).putAll(Map.of("reductionFactor","0.7","adjustedLimitValue","35.00","judgement","不合格","dailyExposureHours","10","weeklyExposureHours","50","weeklyWorkDays",5,"reductionApplicable",true,"overLimitReason","TWA经工时折减后超过PC-TWAa，局部排风不足"));
        rows.add(row("二甲苯","TWA","30","50","mg/m3","P-CHEM","2026-09-11"));
        rows.add(row("二甲苯","STEL","80","100","mg/m3","P-CHEM","2026-09-11"));
        rows.add(row("丙酮","TWA","10","300","mg/m3","P-CHEM","2026-09-10"));
        rows.add(row("苯","TWA","1","6","mg/m3","P-CHEM","2026-09-10"));
        rows.add(row("甲苯","TWA","2","50","mg/m3","P-CHEM","2026-09-10"));
        rows.add(row("电焊烟尘","PE","5","4","mg/m3","P-DUST","2026-09-10"));
        rows.get(rows.size()-1).putAll(Map.of("peakLimitMultiplier",3,"adjustedLimitValue","12","peakExposureMinutes",10,"peakTimesPerShift",2,"peakIntervalMinutes",60));
        String[][] physical={{"噪声","LEX8H","82","85","dB(A)"},{"高温","WBGT","25","28","℃"},{"工频电场","PF_E","1","5","kV/m"},{"工频磁场","PF_B","10","1000","μT"},{"高频电场","HF_E","1","25","V/m"},{"高频磁场","HF_H","1","5","A/m"},{"超高频","UHF","0.01","0.05","mW/cm2"},{"微波","MICROWAVE","0.01","5","mW/cm2"},{"紫外辐射","UVA","0.18","0.4","mW/cm2"},{"激光","LASER","0.001","0.01","W/cm2"},{"手传振动","HAV","2","5","m/s2"},{"X射线","X_GAMMA","0.12","2.5","μSv/h"}};
        for(String[] type:physical){var r=row(type[0],type[1],type[2],type[3],type[4],"P-"+type[1],"2026-09-11");r.put("detectionMode","定点");if(type[1].equals("UVA")){r.put("measurementBodyPart","面部");r.put("measurementBand","UVA 315-400nm");}rows.add(r);}
        payload.put("__RESULTS_JSON__",json(rows));Files.createDirectories(Path.of("target/report-audit-20260917"));Files.writeString(Path.of("target/report-audit-20260917/fixture.json"),json(payload));return payload;
 }
 private Map<String,Object> row(String factor,String metric,String value,String limit,String unit,String point,String date) {
   Map<String,Object> n=new LinkedHashMap<>();n.putAll(Map.of("factorName",factor,"metricType",metric,"valueNum",value,"valueRaw",value,"limitValue",limit,"adjustedLimitValue",limit,"unit",unit,"limitUnit",unit,"reductionFactor","1","judgement","合格"));
   n.putAll(Map.of("workLocation","焊接车间","jobName","焊工","pointCode",point,"sampleCode","TEST-"+point+"-"+metric+"-"+date,"measurementDate",date,"laboratoryDate","2026-09-12","detectionMode","定点长时间","noiseType","非稳态","detectionBasis","验收用检测依据（非真实业务资料）","detectionMethod","验收记录方法"));
   n.putAll(Map.of("instrument","测试仪器QA-017","testingOrganization","验收测试机构","sourceRecordNo","QA-20260917","detectionLimit","验收值0.01 "+unit,"quantificationLimit","验收值0.03 "+unit,"evaluationBasis","验收规则，仅验证软件计算，不作专业限值依据","measurementConditions","测试条件已记录"));return n;
 }
 private XWPFDocument generate(Map<String,String> p,boolean prune,String file) throws Exception {
   ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(InputStream in=getClass().getResourceAsStream("/templates/occupational_report_template_v2.docx")){BeiHaiPeriodicReportGenerator.generate(in,bytes,p,prune);}
   Path out=Path.of("target/report-audit-20260917",file);Files.createDirectories(out.getParent());Files.write(out,bytes.toByteArray());return new XWPFDocument(new ByteArrayInputStream(bytes.toByteArray()));
 }
 private String text(XWPFDocument d){return d.getParagraphs().stream().map(XWPFParagraph::getText).reduce("",(a,b)->a+"\n"+b)+d.getTables().stream().map(XWPFTable::getText).reduce("",(a,b)->a+"\n"+b);}
 @Test void fullAndPrunedReportsKeepEveryPhysicalResultAndColumnAlignment() throws Exception {
   Map<String,String> p=fixture();
   for(boolean prune:new boolean[]{false,true})try(XWPFDocument d=generate(p,prune,prune?"02-pruned.docx":"01-comprehensive.docx")){
     for(String metric:List.of("WBGT","PF_E","PF_B","HF_E","HF_H","UHF","MICROWAVE","UVA","LASER","HAV")){
       assertThat(d.getTables().stream().filter(t->t.getText().contains("P-"+metric)).count()).as(metric+" must be in body and slip").isGreaterThanOrEqualTo(2);
     }
     XWPFTable uv=d.getTables().stream().filter(t->t.getText().contains("样品/测点编号")&&t.getText().contains("UVA 315-400nm")).findFirst().orElseThrow();
     assertThat(uv.getText()).contains("面部","2026-09-11","0.18").doesNotContain("UVC","UVB","头","胸","腹");
     d.getTables().stream().filter(t->t.getText().contains("采样/测量日期")).forEach(t->{int columns=t.getRow(0).getTableCells().size();assertThat(t.getRows()).allSatisfy(r->assertThat(r.getTableCells()).hasSize(columns));});
     XWPFTable ppe=d.getTables().stream().filter(t->t.getText().contains("测试厂家甲")).findFirst().orElseThrow();
     assertThat(ppe.getRow(1).getTableCells().stream().map(XWPFTableCell::getText)).containsExactly("呼吸防护","防尘口罩","测试厂家甲","KN95","颗粒物过滤","焊工","正常使用");
     assertThat(text(d)).contains("1.29","PC-TWAa","35.00").doesNotContain("RAD-01","S-001","见采样记录","见测量记录","71-43-2","79-01-6","均符合");
     assertThat(d.getFooterList()).allSatisfy(f->assertThat(f.getText()).contains("验收测试机构").doesNotContain("北海市疾病预防控制中心"));
     String xml=d.getDocument().xmlText();
     var references=java.util.regex.Pattern.compile("PAGEREF\\s+(_Toc\\d+)").matcher(xml);
     while(references.find())assertThat(xml).as("TOC target "+references.group(1)).contains("w:name=\""+references.group(1)+"\"");
     XWPFTable stats=d.getTables().stream().filter(t->t.getText().contains("按因素计点")).findFirst().orElseThrow();
     XWPFTableRow chemical=stats.getRows().stream().filter(r->r.getCell(1).getText().equals("二甲苯")).findFirst().orElseThrow();
     assertThat(chemical.getCell(2).getText()).isEqualTo("1");assertThat(chemical.getCell(3).getText()).isEqualTo("1");
     XWPFTable summary=d.getTables().stream().filter(t->t.getText().contains("最大结果")).findFirst().orElseThrow();
     var twas=summary.getRows().stream().filter(r->r.getCell(2).getText().equals("二甲苯")&&r.getCell(3).getText().equals("TWA")).toList();
     assertThat(twas).hasSize(1);assertThat(twas.get(0).getCell(4).getText()).isEqualTo("45");
     XWPFTable limits=d.getTables().stream().filter(t->t.getText().contains("采用依据")&&t.getText().contains("二甲苯")).findFirst().orElseThrow();assertThat(limits.getText()).doesNotContain("紫外");
   }
 }
 @Test void pendingResultsCannotProduceAllPassConclusion() throws Exception {
   var p=fixture();ArrayNode rows=(ArrayNode)JSON.readTree(p.get("__RESULTS_JSON__"));((ObjectNode)rows.get(0)).put("judgement","未判定");p.put("__RESULTS_JSON__",json(rows));
   try(var d=generate(p,true,"03-pending.docx")){assertThat(text(d)).contains("无法判定").doesNotContain("均符合");}
 }
 @Test void multipleMeasuredPartsAndBandsRemainAlignedWithoutUnmeasuredColumns() throws Exception {
   var p=fixture();var a=row("紫外辐射","UVA","0.18","0.4","mW/cm2","P-UV","2026-09-11");var b=row("紫外辐射","UVB","0.10","0.2","mW/cm2","P-UV","2026-09-11");
   a.put("measurementBodyPart","面部");a.put("measurementBand","315-400nm");b.put("measurementBodyPart","手部");b.put("measurementBand","280-315nm");p.put("__RESULTS_JSON__",json(List.of(a,b)));
   for(boolean prune:new boolean[]{true,false})try(var d=generate(p,prune,prune?"05-multi-pruned.docx":"06-multi-full.docx")) {
     var table=d.getTables().stream().filter(t->t.getText().contains("样品/测点编号")&&t.getText().contains("P-UV")).findFirst().orElseThrow();
     assertThat(table.getText()).contains("面部","手部","315-400nm","280-315nm").doesNotContain("UVC","胸","腹");
     assertThat(table.getRows()).allSatisfy(r->assertThat(r.getTableCells()).hasSize(table.getRow(0).getTableCells().size()));
   }
 }
 @Test void mixedExposureCanFailWhenEveryIndividualResultPasses() throws Exception {
   var p=fixture();var a=row("甲苯","TWA","30","50","mg/m3","P-1","2026-09-11");var b=row("二甲苯","TWA","30","50","mg/m3","P-1","2026-09-11");
   for(var n:List.of(a,b)){n.put("mixedExposureGroup","MIX-1");n.put("mixedExposureBasis","验收用相同健康效应依据");}
   p.put("__RESULTS_JSON__",json(List.of(a,b)));ObjectNode s=(ObjectNode)JSON.readTree(p.get("__REPORT_SUPPLEMENT_JSON__"));s.put("mixedExposureAssessment","APPLICABLE");p.put("__REPORT_SUPPLEMENT_JSON__",json(s));
   try(var d=generate(p,true,"04-mixed.docx")){assertThat(text(d)).contains("混合接触相加评价不合格1组","1.2","MIX-1").doesNotContain("均符合");}
 }
}
