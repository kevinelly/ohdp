package com.dsf.ohdp;

import static org.assertj.core.api.Assertions.assertThat;

import com.dsf.ohdp.support.BeiHaiPeriodicReportGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.junit.jupiter.api.Test;

class BeiHaiPeriodicReportGeneratorTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void generatesFormalDocxWithoutWordOrTemplateSamples() throws Exception {
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
                Map.entry("reportHeaderName", "北海示范机械"),
                Map.entry("socialCreditCode", "91450502TEST000001"),
                Map.entry("legalRepresentative", "陈海"),
                Map.entry("registeredAddress", "北海市海城区示范路18号"),
                Map.entry("technicalServiceAddress", "北海市海城区示范路18号"),
                Map.entry("contactName", "黄工"),
                Map.entry("contactPhone", "0779-1234567"),
                Map.entry("industryCategory", "制造业/通用设备制造业/金属加工机械制造"),
                Map.entry("detectionScope", "焊接车间正常生产状态下的危害因素。"),
                Map.entry("productionProcess", "钢材经焊接、打磨和装配形成产品。"),
                Map.entry("radiationSourceSummary", "用人单位不存在辐射源项。"),
                Map.entry("vocAnalysisConclusion", "本项目未开展物料挥发性有机组分定性分析。"),
                Map.entry("exclusionScreeningConclusion", "本项目未开展排除性检测。"),
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
                Map.entry("materials", List.of(Map.of(
                        "workplace", "焊接车间", "materialName", "实芯焊丝", "processStage", "焊接",
                        "mainComponents", "铁、锰", "annualAmount", "12t/a", "physicalState", "固态", "positions", "焊工"))),
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
                        Map.entry("limitUnit", "dB(A)"), Map.entry("judgement", "合格")))));
        payload.put("__STAFF_JSON__", json(Map.of(
                "writer", Map.of("name", "张检测", "title", "主管技师", "profession", "职业卫生"),
                "reviewer", Map.of("name", "李审核", "title", "副主任技师", "profession", "职业卫生"),
                "approver", Map.of("name", "王签发", "title", "主任技师", "profession", "公共卫生"))));
        payload.put("__METHODS_JSON__", "{}");

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (InputStream template = getClass().getResourceAsStream("/templates/occupational_report_template.docx")) {
            assertThat(template).isNotNull();
            BeiHaiPeriodicReportGenerator.generate(template, output, payload);
        }
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(output.toByteArray()))) {
            assertThat(document.getTables()).hasSize(108);
            assertThat(document.getTables().get(0).getRow(0).getCell(1).getText()).contains("职20260902-001");
            assertThat(document.getTables().get(44).getNumberOfRows()).isEqualTo(4);
            assertThat(document.getHeaderList())
                    .allSatisfy(header -> assertThat(header.getText()).doesNotContain("XXXX", "职202600XX"));
            assertThat(allText(document)).doesNotContain("[SYSTEM INPUT]", "职202600XX", "20XX年XX月XX日");
            assertThat(redRunText(document)).isBlank();
        }
    }

    private static String json(Object value) throws Exception {
        return JSON.writeValueAsString(value);
    }

    private static String allText(XWPFDocument document) {
        StringBuilder text = new StringBuilder();
        for (XWPFParagraph paragraph : document.getParagraphs()) {
            text.append(paragraph.getText());
        }
        document.getTables().forEach(table -> text.append(table.getText()));
        return text.toString();
    }

    private static String redRunText(XWPFDocument document) {
        StringBuilder text = new StringBuilder();
        document.getParagraphs().forEach(paragraph -> appendRedRuns(paragraph, text));
        document.getTables().forEach(table -> table.getRows().forEach(row -> row.getTableCells()
                .forEach(cell -> cell.getParagraphs().forEach(paragraph -> appendRedRuns(paragraph, text)))));
        return text.toString().trim();
    }

    private static void appendRedRuns(XWPFParagraph paragraph, StringBuilder target) {
        for (XWPFRun run : paragraph.getRuns()) {
            if ("FF0000".equalsIgnoreCase(run.getColor())) {
                target.append(run.text());
            }
        }
    }
}
