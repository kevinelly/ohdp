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
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STSectionMark;
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
                Map.entry("qualificationCertificateImageDataUrl", "data:image/png;base64,"+java.util.Base64.getEncoder().encodeToString(getClass().getResourceAsStream("/report-audit/certificate.png").readAllBytes())),
                Map.entry("qualificationCertificateFileName", "certificate.png"),
                Map.entry("reportPhotos", List.of(Map.of(
                        "fileName", "onsite.png", "caption", "现场检测", "takenDate", "2026-09-01",
                        "imageDataUrl", "data:image/png;base64,"+java.util.Base64.getEncoder().encodeToString(getClass().getResourceAsStream("/report-audit/photo.png").readAllBytes())))),
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

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (InputStream template = getClass().getResourceAsStream("/templates/occupational_report_template_v2.docx")) {
            assertThat(template).isNotNull();
            BeiHaiPeriodicReportGenerator.generate(template, output, payload);
        }
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(output.toByteArray()))) {
            assertThat(document.getTables()).hasSizeGreaterThan(30);
            assertThat(document.getAllPictures()).hasSizeGreaterThanOrEqualTo(4);
            assertThat(document.getTables().get(0).getRow(0).getCell(1).getText()).contains("职20260902-001");
            assertThat(tableContaining(document, "峰面积百分比").getText()).contains("二甲苯");
            assertThat(tableContaining(document, "射线装置名称").getText()).contains("XXQ-2505");
            assertThat(tableContaining(document, "排除职业接触")).isNotNull();
            assertThat(tableContaining(document, "编制人：张检测")).isNotNull();
            assertThat(tableContainingAll(document, "锰及其无机化合物", "二甲苯", "实际检测资料")).isNotNull();
            assertThat(tableContaining(document, "GC-MS-01").getText()).contains("顶空气相色谱-质谱法");
            assertThat(document.getHeaderList())
                    .allSatisfy(header -> assertThat(header.getText()).doesNotContain("XXXX", "职202600XX"));
            document.getHeaderList().stream()
                    .flatMap(header -> header.getParagraphs().stream())
                    .filter(paragraph -> paragraph.getText().contains("职业病危害因素检测"))
                    .forEach(paragraph -> {
                        assertThat(paragraph.getRuns()).hasSize(3);
                        assertThat(paragraph.getRuns().get(0).text())
                                .startsWith("广西北海市示范机械装备制造技术服务有限责任公司")
                                .doesNotContain("     ");
                        assertThat(paragraph.getRuns().get(0).getCTR().sizeOfBrArray()).isZero();
                        assertThat(paragraph.getRuns().get(0).getCTR().getRPr().sizeOfFitTextArray()).isEqualTo(1);
                        assertThat(paragraph.getRuns().get(1).getCTR().sizeOfTabArray()).isEqualTo(1);
                        assertThat(paragraph.getRuns().get(2).text()).isEqualTo("职20260902-001");
                    });
            assertThat(paragraphAfter(document, "1.1 法律、法规与规章")).contains("职业病防治法");
            assertThat(paragraphAfter(document, "1.2 技术标准与规范")).contains("GBZ 159");
            assertThat(paragraphAfter(document, "1.3 基础资料")).contains("现场调查资料");
            assertThat(tocContains(document, "1.3 基础资料")).isTrue();
            assertThat(tocContains(document, "3 用人单位概况")).isTrue();
            assertThat(tocContains(document, "5 检测项目")).isTrue();
            assertThat(tableContaining(document, "采用依据").getText())
                    .contains("锰及其无机化合物")
                    .doesNotContain("0.150000");
            assertThat(tableContainingAll(document, "接触人数", "具体因素").getText())
                    .contains("锰及其无机化合物", "噪声", "6");
            assertThat(tableContainingAll(document, "检测点数", "具体因素").getText())
                    .contains("锰及其无机化合物", "噪声");
            assertThat(tableContainingAll(document, "车间/工作场所", "接触人数").getText())
                    .contains("焊接车间", "喷漆房", "无损检测室");
            assertThat(tableContainingAll(document, "样品/测点编号", "X、γ射线").getText())
                    .contains("无损检测室", "0.12").doesNotContain("R-001");
            assertThat(allText(document))
                    .doesNotContain(
                            "[SYSTEM INPUT]", "职202600XX", "20XX年XX月XX日", "XXX mg/m3", "｛资质证书｝",
                            "表7.4-2 混合接触的岗位汇总及判定表", "表8.3-1 岗位超标原因分析表",
                            "检检测结果报告单", "0.150000",
                            "仅在需要进行折减时列出");
            assertThat(redRunText(document)).isBlank();
        }
    }

    @Test
    void doesNotInventDustMethodOrInstrumentAndAvoidsDoubleBreakAfterShortToc() throws Exception {
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("__PROJECT_JSON__", json(Map.of(
                "projectCode", "职20260902-003",
                "companyName", "北海粉尘检测示例有限公司",
                "samplingDate", "2026年9月1日",
                "reportDate", "2026年9月2日")));
        payload.put("__REPORT_SUPPLEMENT_JSON__", json(Map.of(
                "radiationSourceMode", "NOT_PRESENT",
                "vocAnalysisPerformed", false,
                "exclusionScreeningPerformed", false)));
        payload.put("__POSITIONS_JSON__", json(List.of(Map.of(
                "workLocation", "打磨车间", "jobName", "打磨工", "workerCount", 4))));
        payload.put("__RESULTS_JSON__", json(List.of(Map.ofEntries(
                Map.entry("workLocation", "打磨车间"), Map.entry("jobName", "打磨工"),
                Map.entry("factorName", "其他粉尘"), Map.entry("metricType", "TWA"),
                Map.entry("valueNum", 2.2), Map.entry("unit", "mg/m3"),
                Map.entry("limitValue", 8), Map.entry("limitUnit", "mg/m3"),
                Map.entry("judgement", "合格")))));
        payload.put("__STAFF_JSON__", "{}");
        payload.put("__METHODS_JSON__", "{}");

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (InputStream template = getClass().getResourceAsStream("/templates/occupational_report_template_v2.docx")) {
            assertThat(template).isNotNull();
            BeiHaiPeriodicReportGenerator.generate(template, output, payload);
        }
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(output.toByteArray()))) {
            assertThat(allText(document)).contains("其他粉尘")
                    .doesNotContain("粉尘采样器 FC-01", "电子天平 BAL-01", "见采样记录");
            assertThat(sectionTypeBeforeHeading(document, "1 检测与评价依据"))
                    .isEqualTo(STSectionMark.CONTINUOUS);
        }
    }

    @Test
    void removesVocQualitativeTableWhenAnalysisWasNotPerformed() throws Exception {
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("__PROJECT_JSON__", json(Map.of("projectCode", "职20260902-002")));
        payload.put("__REPORT_SUPPLEMENT_JSON__", json(Map.of(
                "vocAnalysisPerformed", false,
                "vocAnalysisConclusion", "本项目未开展物料挥发性有机组分定性分析。")));
        payload.put("__POSITIONS_JSON__", "[]");
        payload.put("__RESULTS_JSON__", "[]");
        payload.put("__STAFF_JSON__", "{}");
        payload.put("__METHODS_JSON__", "{}");

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (InputStream template = getClass().getResourceAsStream("/templates/occupational_report_template_v2.docx")) {
            assertThat(template).isNotNull();
            BeiHaiPeriodicReportGenerator.generate(template, output, payload);
        }
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(output.toByteArray()))) {
            assertThat(document.getTables()).hasSizeLessThan(108);
            assertThat(allText(document))
                    .doesNotContain("表3.2-2 物料挥发性有机组分定性结果表")
                    .contains("本项目未开展物料挥发性有机组分定性分析");
        }
    }

    @Test
    void keepsNotApplicableSectionsWhenRequested() throws Exception {
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("__PROJECT_JSON__", json(Map.of(
                "projectCode", "职20260902-004",
                "companyName", "完整模板模式示例有限公司")));
        payload.put("__REPORT_SUPPLEMENT_JSON__", json(Map.of(
                "radiationSourceMode", "NOT_PRESENT",
                "vocAnalysisPerformed", false,
                "exclusionScreeningPerformed", false)));
        payload.put("__POSITIONS_JSON__", "[]");
        payload.put("__RESULTS_JSON__", "[]");
        payload.put("__STAFF_JSON__", "{}");
        payload.put("__METHODS_JSON__", "{}");

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (InputStream template = getClass().getResourceAsStream("/templates/occupational_report_template_v2.docx")) {
            assertThat(template).isNotNull();
            BeiHaiPeriodicReportGenerator.generate(template, output, payload, false);
        }
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(output.toByteArray()))) {
            assertThat(document.getTables()).hasSize(108);
            assertThat(allText(document))
                    .contains(
                            "表3.2-2 物料挥发性有机组分定性结果表",
                            "4.2 排除性检测及接触判定",
                            "7.1.1 粉尘中游离二氧化硅含量",
                            "7.3 放射性因素",
                            "不涉及");
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

    private static String paragraphAfter(XWPFDocument document, String heading) {
        List<XWPFParagraph> paragraphs = document.getParagraphs();
        for (int i = 0; i + 1 < paragraphs.size(); i++) {
            if (paragraphs.get(i).getText().trim().equals(heading)) {
                return paragraphs.get(i + 1).getText();
            }
        }
        return "";
    }

    private static org.apache.poi.xwpf.usermodel.XWPFTable tableContaining(XWPFDocument document, String text) {
        return document.getTables().stream().filter(table -> table.getText().contains(text)).findFirst().orElse(null);
    }

    private static org.apache.poi.xwpf.usermodel.XWPFTable tableContainingAll(
            XWPFDocument document, String... texts) {
        return document.getTables().stream().filter(table -> {
            for (String text : texts) {
                if (!table.getText().contains(text)) {
                    return false;
                }
            }
            return true;
        }).findFirst().orElse(null);
    }

    private static boolean tocContains(XWPFDocument document, String heading) {
        return document.getParagraphs().stream()
                .map(XWPFParagraph::getText)
                .anyMatch(text -> text.startsWith(heading) && !text.trim().equals(heading));
    }

    private static STSectionMark.Enum sectionTypeBeforeHeading(XWPFDocument document, String heading) {
        List<XWPFParagraph> paragraphs = document.getParagraphs();
        int headingIndex = -1;
        for (int i = 0; i < paragraphs.size(); i++) {
            if (paragraphs.get(i).getText().trim().equals(heading)) {
                headingIndex = i;
                break;
            }
        }
        for (int i = headingIndex - 1; i >= 0; i--) {
            XWPFParagraph paragraph = paragraphs.get(i);
            if (paragraph.getCTP().isSetPPr() && paragraph.getCTP().getPPr().isSetSectPr()
                    && paragraph.getCTP().getPPr().getSectPr().isSetType()) {
                return paragraph.getCTP().getPPr().getSectPr().getType().getVal();
            }
        }
        return null;
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
