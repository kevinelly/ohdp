package com.dsf.ohdp.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFHeader;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;

/**
 * Cross-platform generator for the 2026 BeiHai periodic occupational-hazard report.
 *
 * <p>The source template is a DOCX copy faithfully converted from the customer supplied DOC file.
 * This implementation intentionally preserves unperformed/unconfirmed sections. Conditional tables
 * are only populated or marked not-applicable after an explicit business confirmation.</p>
 */
public final class BeiHaiPeriodicReportGenerator {

    private static final ObjectMapper JSON = new ObjectMapper();

    private BeiHaiPeriodicReportGenerator() {
    }

    public static void generate(InputStream template, OutputStream output, Map<String, String> replacements)
            throws IOException {
        try (XWPFDocument document = new XWPFDocument(template)) {
            JsonNode project = readJson(replacements.get("__PROJECT_JSON__"));
            JsonNode supplement = readJson(replacements.get("__REPORT_SUPPLEMENT_JSON__"));
            JsonNode positions = readJson(replacements.get("__POSITIONS_JSON__"));
            JsonNode results = readJson(replacements.get("__RESULTS_JSON__"));
            JsonNode staff = readJson(replacements.get("__STAFF_JSON__"));

            replaceHeaders(document, supplement.path("reportHeaderName").asText(project.path("companyName").asText()),
                    project.path("projectCode").asText());
            applyPlainReplacements(document, replacements);
            fillCover(document, project);
            fillStatementAndStaff(document, project, staff);
            fillNarratives(document, project, supplement);
            fillCompanyProfile(document, project, supplement);
            fillResources(document, supplement);
            fillPositionsAndSampling(document, positions, supplement);
            fillDetectionResults(document, positions, results, supplement);
            fillConclusion(document, project, supplement);
            fillResultSlip(document, project, results);
            replaceAll(document, "20XX年XX月XX日", text(project, "reportDate"));
            removeRedInstructions(document);

            document.getSettings().setUpdateFields();
            document.write(output);
        }
    }

    private static JsonNode readJson(String value) throws IOException {
        if (value == null || value.isBlank()) {
            return JSON.createObjectNode();
        }
        return JSON.readTree(value);
    }

    private static void applyPlainReplacements(XWPFDocument document, Map<String, String> replacements) {
        replacements.forEach((key, value) -> {
            if (key != null && !key.startsWith("__") && !key.isBlank()) {
                replaceAll(document, key, value == null ? "" : value);
            }
        });
    }

    private static void fillCover(XWPFDocument document, JsonNode project) {
        XWPFTable coverNo = table(document, 1);
        setCell(coverNo, 1, 2, text(project, "projectCode"));
        XWPFTable cover = table(document, 2);
        setCell(cover, 1, 2, text(project, "sampleName"));
        setCell(cover, 2, 2, text(project, "companyName"));
        setCell(cover, 3, 2, text(project, "companyAddress"));
        setCell(cover, 4, 2, fallback(text(project, "submitUnit"), "/"));

        String entrust = text(project, "entrustUnit");
        boolean separateEntrust = !entrust.isBlank() && !"/".equals(entrust)
                && !entrust.equals(text(project, "companyName"));
        if (separateEntrust) {
            setCell(cover, 5, 2, entrust);
        } else if (cover != null && cover.getNumberOfRows() >= 5) {
            cover.removeRow(4);
        }
        if (cover != null) {
            setCell(cover, Math.max(1, cover.getNumberOfRows() - 1), 2,
                    fallback(text(project, "detectType"), "定期检测"));
        }
    }

    private static void fillStatementAndStaff(XWPFDocument document, JsonNode project, JsonNode staff) {
        String entrust = text(project, "entrustUnit");
        boolean separateEntrust = !entrust.isBlank() && !"/".equals(entrust)
                && !entrust.equals(text(project, "companyName"));
        String orgName = text(project, "orgName");
        String statement = separateEntrust
                ? orgName + "遵守国家有关法律、法规、规章和标准规范，受" + entrust + "委托，在对"
                        + text(project, "companyName")
                        + "提供职业病危害因素检测服务过程中，坚持客观、真实、公正的原则，并对所出具的《检测报告》承担法律责任。"
                : orgName + "遵守国家有关法律、法规、规章和标准规范，在对"
                        + text(project, "companyName")
                        + "提供职业病危害因素检测服务过程中，坚持客观、真实、公正的原则，并对所出具的《检测报告》承担法律责任。";
        setParagraphContaining(document, "遵守国家有关法律、法规、规章和标准规范", statement);
        setParagraphContaining(document, "20XX年XX月XX日", text(project, "statementDate"));

        XWPFTable table = table(document, 3);
        fillStaffRow(table, 2, staff.path("writer"));
        fillStaffRow(table, 4, staff.path("reviewer"));
        fillStaffRow(table, 7, staff.path("approver"));
    }

    private static void fillStaffRow(XWPFTable table, int row, JsonNode staff) {
        setCell(table, row, 2, text(staff, "name"));
        setCell(table, row, 3, text(staff, "title"));
        setCell(table, row, 4, text(staff, "profession"));
    }

    private static void fillNarratives(XWPFDocument document, JsonNode project, JsonNode supplement) {
        insertBodyAfterHeading(document, "1.2 技术标准与规范", text(project, "evaluationBasis"));
        setParagraphContaining(document, "为预防、控制和消除项目可能产生的职业病危害", text(project, "taskOrigin"));
        insertBodyAfterHeading(document, "2.3 检测范围", text(supplement, "detectionScope"));
        setParagraphContaining(document, "介绍从原材料投入到成品产出全过程的工艺描述", text(supplement, "productionProcess"));
        setParagraphContaining(document, "此表仅在进行了物料挥发性有机组分定性时需列出", text(supplement, "vocAnalysisConclusion"));

        String radiation = text(supplement, "radiationSourceSummary");
        if (!radiation.isBlank()) {
            setParagraphContaining(document, "无辐射源项时：用人单位不存在辐射源项", radiation);
            if (radiation.contains("不存在") || radiation.contains("无辐射") || radiation.contains("不涉及")) {
                clearParagraphContaining(document, "放射性因素具体情况及检测结果详见本机构另行出具的定期检测报告");
            }
        }
        insertBodyAfterHeading(document, "4.2 排除性检测及接触判定", text(supplement, "exclusionScreeningConclusion"));
        setParagraphContaining(document, "列出现场检测具体日期", text(supplement, "productionCondition"));
        setParagraphContaining(document, "描述现场检测的布点情况", text(supplement, "samplingLayoutDescription"));
        setParagraphContaining(document, "现场检测条件已满足以下要求", text(supplement, "detectionConditions"));
        setParagraphContaining(document, "描述现场检测时当地的温度、湿度和气压", text(supplement, "environmentConditions"));
        setParagraphContaining(document, "列出所有现场检测人员", text(supplement, "onsiteInspectors"));
        setParagraphContaining(document, "如存在检测计划中有未开展采样与测量", text(supplement, "planDeviation"));
        clearParagraphContaining(document, "如计划无变更则写为");
    }

    private static void fillCompanyProfile(XWPFDocument document, JsonNode project, JsonNode supplement) {
        XWPFTable table = table(document, 4);
        setCell(table, 1, 2, text(project, "companyName"));
        setCell(table, 2, 2, text(supplement, "socialCreditCode"));
        setCell(table, 2, 4, text(supplement, "legalRepresentative"));
        setCell(table, 3, 2, fallback(text(supplement, "registeredAddress"), text(project, "companyAddress")));
        setCell(table, 4, 2, fallback(text(supplement, "technicalServiceAddress"), text(project, "companyAddress")));
        setCell(table, 5, 2, text(supplement, "contactName"));
        setCell(table, 5, 4, text(supplement, "contactPhone"));
        setCell(table, 6, 2, text(supplement, "industryCategory"));
        setCell(table, 7, 2, text(supplement, "commissioningDate"));
        setCell(table, 7, 4, text(supplement, "economicType"));
        setCell(table, 8, 2, text(supplement, "companyScale"));
        setCell(table, 8, 4, text(supplement, "employeeCount"));
        setCell(table, 9, 2, text(supplement, "outsourcedWorkerCount"));
        setCell(table, 9, 4, text(supplement, "dispatchedWorkerCount"));
    }

    private static void fillResources(XWPFDocument document, JsonNode supplement) {
        List<JsonNode> materials = nodes(supplement.path("materials"));
        if (materials.isEmpty() && supplement.path("materialsConfirmedNotApplicable").asBoolean(false)) {
            materials = List.of(object("workplace", "不涉及", "materialName", "/", "processStage", "/",
                    "mainComponents", "/", "annualAmount", "/", "physicalState", "/", "positions", "/"));
        }
        fillTable(document, 5, 1, materials, n -> List.of(text(n, "workplace"), text(n, "materialName"),
                text(n, "processStage"), text(n, "mainComponents"), text(n, "annualAmount"),
                text(n, "physicalState"), text(n, "positions")));

        List<JsonNode> equipment = nodes(supplement.path("equipment"));
        if (equipment.isEmpty() && supplement.path("equipmentConfirmedNotApplicable").asBoolean(false)) {
            equipment = List.of(object("workplace", "不涉及", "location", "/", "equipmentName", "/",
                    "totalCount", "/", "runningCount", "/", "operatingPositions", "/"));
        }
        fillTable(document, 10, 2, equipment, n -> List.of(text(n, "workplace"), text(n, "location"),
                text(n, "equipmentName"), text(n, "totalCount"), text(n, "runningCount"), text(n, "operatingPositions")));

        List<JsonNode> facilities = nodes(supplement.path("protectionFacilities"));
        if (facilities.isEmpty() && supplement.path("protectionFacilitiesConfirmedNotApplicable").asBoolean(false)) {
            facilities = List.of(object("workplace", "不涉及", "locationOrPosition", "/", "facilityName", "/",
                    "facilityType", "/", "totalCount", "/", "runningCount", "/"));
        }
        fillTable(document, 11, 2, facilities, n -> List.of(text(n, "workplace"), text(n, "locationOrPosition"),
                text(n, "facilityName"), text(n, "facilityType"), text(n, "totalCount"), text(n, "runningCount")));

        List<JsonNode> ppes = nodes(supplement.path("personalProtectiveEquipment"));
        if (ppes.isEmpty() && supplement.path("ppeConfirmedNotApplicable").asBoolean(false)) {
            ppes = List.of(object("ppeName", "不涉及", "manufacturer", "/", "model", "/",
                    "protectionParameters", "/", "positions", "/", "usageStatus", "/", "category", "/"));
        }
        fillTable(document, 12, 2, ppes, n -> List.of(text(n, "ppeName"), text(n, "manufacturer"),
                text(n, "model"), text(n, "protectionParameters"), text(n, "positions"),
                text(n, "usageStatus"), text(n, "category")));
    }

    private static void fillPositionsAndSampling(XWPFDocument document, JsonNode positions, JsonNode supplement) {
        fillTable(document, 13, 2, nodes(positions), n -> List.of(
                text(n, "workLocation"), text(n, "jobName"), text(n, "shiftSystem"), text(n, "workingHours"),
                text(n, "workerCount"), text(n, "workerCount"), text(n, "operationType"), text(n, "workLocation"),
                text(n, "operationContent"), text(n, "hazards"), text(n, "operationDuration"), "",
                "相对稳定", "正常作业时/连续"));

        fillTable(document, 15, 2, nodes(supplement.path("samplingPlans")), n -> List.of(
                text(n, "workplace"), text(n, "jobName"), text(n, "operationType"), text(n, "workersPerShift"),
                text(n, "targetLocationTiming"), text(n, "factorName"), text(n, "limitType"), text(n, "contactType"),
                text(n, "detectionMode"), text(n, "detectionTimeType"), text(n, "pointsPerDay"),
                text(n, "timesPerDay"), text(n, "days")));
    }

    private static void fillDetectionResults(XWPFDocument document, JsonNode positions, JsonNode results, JsonNode supplement) {
        List<JsonNode> all = nodes(results);
        fillTable(document, 17, 1, filterMetric(all, "TWA", "CTWA"), n -> List.of(
                text(n, "workLocation"), text(n, "jobName"), fallback(text(n, "detectionMode"), "定点长时间检测"),
                text(n, "factorName"), resultValue(n)));
        fillTable(document, 18, 1, filterMetric(all, "STEL", "CSTE"), n -> List.of(
                text(n, "workLocation"), text(n, "jobName"), text(n, "workLocation"), text(n, "factorName"), resultValue(n)));
        fillTable(document, 19, 1, filterMetric(all, "CME", "MAC"), n -> List.of(
                text(n, "workLocation"), text(n, "jobName"), text(n, "workLocation"), text(n, "factorName"), resultValue(n)));
        fillTable(document, 20, 2, filterMetric(all, "PE", "CPE"), n -> List.of(
                text(n, "workLocation"), text(n, "jobName"), text(n, "workLocation"), text(n, "factorName"),
                resultValue(n), "", "", "", "", ""));
        fillTable(document, 21, 1, filterMetric(all, "LEX8H", "LEX,8H", "LEX,W"), n -> List.of(
                text(n, "workLocation"), text(n, "jobName"), "定点检测", fallback(text(n, "metricType"), "LEX,8h"), resultValue(n)));
        fillTable(document, 22, 1, filterMetric(all, "LAEQ"), n -> List.of(
                text(n, "workLocation"), text(n, "jobName"), text(n, "workLocation"), "稳态", resultValue(n)));

        fillTable(document, 45, 2, all, n -> List.of(
                text(n, "workLocation"), text(n, "jobName"), text(n, "factorName"), text(n, "metricType"),
                resultValue(n), text(n, "metricType"), text(n, "limitValue"),
                fallback(text(n, "limitUnit"), text(n, "unit")), text(n, "judgement")));
        fillTable(document, 47, 1, nodes(positions), n -> List.of(
                text(n, "workLocation"), text(n, "jobName"), text(n, "hazards"), text(n, "workerCount")));

        List<JsonNode> failed = all.stream().filter(n -> "不合格".equals(text(n, "judgement"))).toList();
        fillTable(document, 48, 1, failed, n -> List.of(
                text(n, "workLocation"), text(n, "jobName"), text(n, "workLocation"), text(n, "factorName"),
                overLimitRatio(n), text(n, "metricType"), text(supplement, "overLimitSummary")));
    }

    private static void fillConclusion(XWPFDocument document, JsonNode project, JsonNode supplement) {
        String risk = "根据《国家卫生健康委办公厅关于公布建设项目职业病危害风险分类管理目录的通知》"
                + "（国卫办职健发〔2021〕5号）的规定，并结合存在的危害因素及浓度、强度进行分析，"
                + text(project, "companyName") + "工作场所职业病危害风险分类为" + text(supplement, "riskCategory") + "。";
        setParagraphContaining(document, "根据《国家卫生健康委办公厅关于公布建设项目职业病危害风险分类管理目录的通知》", risk);
        setParagraphContaining(document, "明确是否存在超标岗位", text(supplement, "overLimitSummary"));
        setParagraphContaining(document, "从职业病防护设施、个人使用的职业病防护用品等方面指出", text(supplement, "majorProblems"));
        setParagraphContaining(document, "针对用人单位在职业病防治方面存在的主要问题", text(supplement, "recommendations"));
        clearParagraphContaining(document, "对GBZ 2.1中标注致癌性标识");
        clearParagraphContaining(document, "对用人单位提出进一步做好职业病防治工作的管理和持续改进建议");
    }

    private static void fillResultSlip(XWPFDocument document, JsonNode project, JsonNode results) {
        if (document.getTables().size() < 68) {
            return;
        }
        XWPFTable slip = table(document, 66);
        setCell(slip, 1, 2, text(project, "projectCode") + "号");
        setCell(slip, 2, 2, text(project, "companyName"));
        setCell(slip, 3, 2, text(project, "detectType"));
        setCell(slip, 4, 2, text(project, "reportDate"));

        XWPFTable summary = table(document, 67);
        setCell(summary, 1, 2, text(project, "companyName"));
        setCell(summary, 2, 2, text(project, "companyAddress"));
        List<String> factors = new ArrayList<>();
        for (JsonNode result : nodes(results)) {
            String factor = text(result, "factorName");
            if (!factor.isBlank() && !factors.contains(factor)) {
                factors.add(factor);
            }
        }
        setCell(summary, 3, 2, String.join("、", factors));

        XWPFTable signature = table(document, 68);
        setCell(signature, 1, 1, "编制人：" + text(project, "writerName"));
        setCell(signature, 1, 2, "审核人：" + text(project, "reviewerName"));
        setCell(signature, 1, 3, "签发人：" + text(project, "approverName"));
    }

    private static void replaceHeaders(XWPFDocument document, String headerName, String projectCode) {
        for (XWPFHeader header : document.getHeaderList()) {
            for (XWPFParagraph paragraph : header.getParagraphs()) {
                replaceParagraphText(paragraph, "XXXX", headerName);
                replaceParagraphText(paragraph, "职202600XX", projectCode);
                replaceParagraphText(paragraph, "职 202600XX", projectCode);
            }
        }
    }

    private static void replaceAll(XWPFDocument document, String needle, String replacement) {
        for (XWPFParagraph paragraph : allParagraphs(document)) {
            replaceParagraphText(paragraph, needle, replacement);
        }
        for (XWPFHeader header : document.getHeaderList()) {
            for (XWPFParagraph paragraph : header.getParagraphs()) {
                replaceParagraphText(paragraph, needle, replacement);
            }
        }
    }

    private static boolean setParagraphContaining(XWPFDocument document, String needle, String replacement) {
        if (needle == null || needle.isBlank() || replacement == null || replacement.isBlank()) {
            return false;
        }
        for (XWPFParagraph paragraph : document.getParagraphs()) {
            if (paragraph.getText().contains(needle)) {
                setParagraphText(paragraph, replacement);
                return true;
            }
        }
        return false;
    }

    private static boolean clearParagraphContaining(XWPFDocument document, String needle) {
        if (needle == null || needle.isBlank()) {
            return false;
        }
        for (XWPFParagraph paragraph : document.getParagraphs()) {
            if (paragraph.getText().contains(needle)) {
                setParagraphText(paragraph, "");
                return true;
            }
        }
        return false;
    }

    private static void insertBodyAfterHeading(XWPFDocument document, String heading, String body) {
        if (body == null || body.isBlank()) {
            return;
        }
        List<XWPFParagraph> paragraphs = document.getParagraphs();
        for (int i = 0; i < paragraphs.size(); i++) {
            if (!paragraphs.get(i).getText().contains(heading)) {
                continue;
            }
            for (int next = i + 1; next < paragraphs.size(); next++) {
                String existing = paragraphs.get(next).getText();
                if (!existing.isBlank()) {
                    setParagraphText(paragraphs.get(next), body);
                    return;
                }
            }
        }
    }

    private static void removeRedInstructions(XWPFDocument document) {
        for (XWPFParagraph paragraph : allParagraphs(document)) {
            for (XWPFRun run : paragraph.getRuns()) {
                String color = run.getColor();
                if (color != null && "FF0000".equalsIgnoreCase(color)) {
                    setRunText(run, "");
                    run.setColor("000000");
                }
            }
        }
        for (XWPFHeader header : document.getHeaderList()) {
            for (XWPFParagraph paragraph : header.getParagraphs()) {
                for (XWPFRun run : paragraph.getRuns()) {
                    String color = run.getColor();
                    if (color != null && "FF0000".equalsIgnoreCase(color)) {
                        setRunText(run, "");
                        run.setColor("000000");
                    }
                }
            }
        }
    }

    private static void fillTable(XWPFDocument document, int tableIndex, int headerRows, List<JsonNode> items,
                                  Function<JsonNode, List<String>> builder) {
        if (items == null || items.isEmpty()) {
            return;
        }
        XWPFTable table = table(document, tableIndex);
        if (table == null) {
            return;
        }
        while (table.getNumberOfRows() > headerRows) {
            table.removeRow(headerRows);
        }
        for (JsonNode item : items) {
            addRow(table, builder.apply(item));
        }
    }

    private static void addRow(XWPFTable table, List<String> values) {
        XWPFTableRow row = table.createRow();
        while (row.getTableCells().size() < values.size()) {
            row.createCell();
        }
        for (int i = 0; i < values.size(); i++) {
            setCellText(row.getCell(i), values.get(i));
        }
    }

    private static void setCell(XWPFTable table, int row, int column, String value) {
        if (table == null || row < 1 || column < 1 || table.getNumberOfRows() < row) {
            return;
        }
        XWPFTableRow tableRow = table.getRow(row - 1);
        while (tableRow.getTableCells().size() < column) {
            tableRow.createCell();
        }
        setCellText(tableRow.getCell(column - 1), value);
    }

    private static void setCellText(XWPFTableCell cell, String value) {
        if (cell == null) {
            return;
        }
        XWPFParagraph paragraph;
        if (cell.getParagraphs().isEmpty()) {
            paragraph = cell.addParagraph();
        } else {
            paragraph = cell.getParagraphs().get(0);
        }
        setParagraphText(paragraph, value == null ? "" : value);
        for (int i = cell.getParagraphs().size() - 1; i > 0; i--) {
            cell.removeParagraph(i);
        }
    }

    private static void setParagraphText(XWPFParagraph paragraph, String value) {
        List<XWPFRun> runs = paragraph.getRuns();
        XWPFRun first = runs.isEmpty() ? paragraph.createRun() : runs.get(0);
        setRunText(first, value == null ? "" : value);
        first.setColor("000000");
        for (int i = 1; i < runs.size(); i++) {
            setRunText(runs.get(i), "");
        }
    }

    private static void replaceParagraphText(XWPFParagraph paragraph, String needle, String replacement) {
        if (needle == null || needle.isBlank() || !paragraph.getText().contains(needle)) {
            return;
        }
        setParagraphText(paragraph, paragraph.getText().replace(needle, replacement == null ? "" : replacement));
    }

    private static void setRunText(XWPFRun run, String value) {
        int count = run.getCTR().sizeOfTArray();
        if (count == 0) {
            run.setText(value);
            return;
        }
        run.setText(value, 0);
        for (int i = count - 1; i > 0; i--) {
            run.getCTR().removeT(i);
        }
    }

    private static List<XWPFParagraph> allParagraphs(XWPFDocument document) {
        List<XWPFParagraph> paragraphs = new ArrayList<>(document.getParagraphs());
        for (XWPFTable table : document.getTables()) {
            collectTableParagraphs(table, paragraphs);
        }
        return paragraphs;
    }

    private static void collectTableParagraphs(XWPFTable table, List<XWPFParagraph> target) {
        for (XWPFTableRow row : table.getRows()) {
            for (XWPFTableCell cell : row.getTableCells()) {
                target.addAll(cell.getParagraphs());
                for (IBodyElement bodyElement : cell.getBodyElements()) {
                    if (bodyElement instanceof XWPFTable nested) {
                        collectTableParagraphs(nested, target);
                    }
                }
            }
        }
    }

    private static XWPFTable table(XWPFDocument document, int oneBasedIndex) {
        int index = oneBasedIndex - 1;
        return index >= 0 && index < document.getTables().size() ? document.getTables().get(index) : null;
    }

    private static List<JsonNode> nodes(JsonNode node) {
        List<JsonNode> values = new ArrayList<>();
        if (node != null && node.isArray()) {
            node.forEach(values::add);
        } else if (node != null && !node.isMissingNode() && !node.isNull() && !node.isObject()) {
            values.add(node);
        }
        return values;
    }

    private static List<JsonNode> filterMetric(List<JsonNode> results, String... codes) {
        List<String> normalized = new ArrayList<>();
        for (String code : codes) {
            normalized.add(normalizeMetric(code));
        }
        return results.stream()
                .filter(node -> normalized.contains(normalizeMetric(text(node, "metricType"))))
                .toList();
    }

    private static String normalizeMetric(String value) {
        return value == null ? "" : value.toUpperCase(Locale.ROOT).replace(" ", "");
    }

    private static String resultValue(JsonNode node) {
        return fallback(text(node, "valueRaw"), text(node, "valueNum"));
    }

    private static String overLimitRatio(JsonNode node) {
        try {
            BigDecimal limit = new BigDecimal(text(node, "limitValue"));
            BigDecimal value = new BigDecimal(resultValue(node));
            if (limit.signum() > 0) {
                return value.divide(limit, 2, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    private static String text(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return "";
        }
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? "" : value.asText("");
    }

    private static String fallback(String value, String fallback) {
        return value == null || value.isBlank() ? (fallback == null ? "" : fallback) : value;
    }

    private static JsonNode object(String... entries) {
        var object = JSON.createObjectNode();
        for (int i = 0; i + 1 < entries.length; i += 2) {
            object.put(entries[i], entries[i + 1]);
        }
        return object;
    }
}
