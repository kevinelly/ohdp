package com.dsf.ohdp.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Base64;
import java.util.function.Function;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.UnderlinePatterns;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFHeader;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.apache.poi.util.Units;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STSectionMark;
import org.apache.xmlbeans.XmlCursor;

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
        generate(template, output, replacements, true);
    }

    public static void generate(
            InputStream template,
            OutputStream output,
            Map<String, String> replacements,
            boolean deleteNotApplicable) throws IOException {
        try (XWPFDocument document = new XWPFDocument(template)) {
            JsonNode project = readJson(replacements.get("__PROJECT_JSON__"));
            JsonNode supplement = readJson(replacements.get("__REPORT_SUPPLEMENT_JSON__"));
            JsonNode positions = readJson(replacements.get("__POSITIONS_JSON__"));
            JsonNode results = readJson(replacements.get("__RESULTS_JSON__"));
            JsonNode staff = readJson(replacements.get("__STAFF_JSON__"));
            JsonNode methods = readJson(replacements.get("__METHODS_JSON__"));

            replaceHeaders(document, supplement.path("reportHeaderName").asText(project.path("companyName").asText()),
                    project.path("projectCode").asText());
            applyPlainReplacements(document, replacements);
            if (project.isObject() && !nodes(results).isEmpty()) {
                ((com.fasterxml.jackson.databind.node.ObjectNode)project).put("sampleName", distinctJoined(nodes(results), "factorName"));
            }
            fillCover(document, project);
            fillStatementAndStaff(document, project, staff);
            fillNarratives(document, project, supplement);
            fillCompanyProfile(document, project, supplement);
            fillResources(document, supplement);
            fillPositionsAndSampling(document, positions, supplement);
            fillDetectionResults(document, positions, results, supplement);
            fillConclusion(document, project, results, supplement);
            fillResultSlip(document, project, results);
            fillResultDetailSheets(document, project, results, methods, supplement);
            fillAppendixLimitTables(document, nodes(results), false);
            finalizeExclusionScreeningSection(document, supplement, false);
            finalizeRadiationSourceSection(document, project, supplement, results, false);
            finalizeVocAnalysisSection(document, supplement, false);
            ReportTableRepair.apply(document, project, supplement, positions, results, deleteNotApplicable);
            embedQualificationCertificate(document, supplement);
            embedReportPhotos(document, supplement);
            useContinuousSectionBreakBeforeHeading(document, "1 检测与评价依据");
            compactSectionBreakAfterHeading(document, "4.1 岗位情况及职业病危害因素接触情况");
            compactSectionBreakBeforeHeading(document, "6.3 检测条件要求");
            removeBlankParagraphsBeforeHeading(document, "7 检测结果分析与评价");
            removeTrailingBlankParagraphs(document);
            replaceAll(document, "20XX年XX月XX日", text(project, "reportDate"));
            removeRedInstructions(document);
            replaceAll(document, "（仅在需要进行折减时列出）", "");
            removeStandaloneConversionArtifacts(document);
            replaceAll(document, "检检测结果报告单", "检测结果报告单");

            finalizeAuditedTextAndLayout(document, project, supplement, results);
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

    private static void finalizeAuditedTextAndLayout(XWPFDocument document, JsonNode project, JsonNode supplement, JsonNode results) {
        replaceAll(document, "北海市疾病预防控制中心（北海市卫生监督所）", text(project,"orgName"));
        replaceAll(document, "报告编号（任务/受理编号）：：", "报告编号（任务/受理编号）：");
        replaceAll(document, "365nUVA", "365nm UVA");
        replaceAll(document, "总粉尘/呼吸性粉尘/化学有害因素（化学因素写实际名称）", "粉尘及化学有害因素检测结果");
        setParagraphContaining(document, "本检验检测报告书总页数不包括", "8. 本报告从封面起连续计页，总页数包含封面、说明、正文及检测结果单。");
        String factors = distinctJoined(nodes(results), "factorName");
        boolean notice = false;
        // This paragraph belongs to chapter 5, not to the project scope description.
        for (XWPFParagraph paragraph : allParagraphs(document)) {
            String text = paragraph.getText();
            if(text.trim().equals("1 检测与评价依据"))paragraph.setPageBreak(true);
            if(text.replaceAll("\\s", "").equals("检验检测报告")) {
                paragraph.getCTP().setPPr(org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPPr.Factory.newInstance());
                while(!paragraph.getRuns().isEmpty())paragraph.removeRun(0);
                var title=paragraph.createRun();title.setText("检验检测报告");title.setFontSize(28);title.setBold(true);
                paragraph.setAlignment(ParagraphAlignment.CENTER);paragraph.setSpacingBefore(240);paragraph.setSpacingAfter(500);
            }
            if(text.trim().equals("注意事项"))notice=true;
            if(text.replaceAll("\\s", "").equals("声明")) paragraph.setPageBreak(true);
            if(text.contains("封二") && text.contains("BHCDC"))setParagraphText(paragraph,"");
            if(text.trim().equals("注意事项"))paragraph.setPageBreak(true);
            if(notice) {
                paragraph.setSpacingBefore(0);paragraph.setSpacingAfter(40);paragraph.setSpacingBetween(1.1);
                for(XWPFRun run:paragraph.getRuns())run.setFontSize(10);
            }
            if(text.contains("监督电话"))notice=false;
            if (text.contains("检测项目与") && (text.contains("合同") || text.contains("委托")) && (text.contains("一致") || text.contains("说明"))) {
                setParagraphText(paragraph, "本次实际检测项目：" + factors + "。计划与实施情况：" + text(supplement,"planDeviation"));
            }
            if(text.contains("针对表4.1-1") || text.contains("针对表 4.1-1")) setParagraphText(paragraph,"本次实际检测项目："+factors+"。计划与实施情况："+text(supplement,"planDeviation"));
            if(text.contains("如有只识别不检测") || text.contains("如存在委托检测（外包")) setParagraphText(paragraph,"");
            if (text.contains("照片需要有地点") || text.contains("列出其他认为有必要的附件")
                    || text.contains("化学因素写实际名称") || text.contains("此行，正常不需要列出")) setParagraphText(paragraph, "");
            if (text.contains("根据各类放射性因素的实际情况填写")) setParagraphText(paragraph, "放射性因素控制值及依据见各项结果的有效限值和检测依据；未开展项目不作评价。");
            if (text.contains("确定等级") && text.contains("根据")) setParagraphText(paragraph, "本项目职业病危害风险分类：" + text(supplement,"riskCategory") + "。");
            if (text.contains("照片需要") || text.contains("附件（其他）")) setParagraphText(paragraph, "");
            if(text.startsWith("根据用人单位劳动者接触") || text.trim().equals("附件4 用人单位职业病危害风险分级"))setParagraphText(paragraph,"");
            if(text.contains("检测对象指个体检测") || text.contains("限值类型，危害因素")
                    || text.contains("接触类型填写") || text.contains("检测方式填写") || text.contains("检测时间类型填写")
                    || text.contains("按申报系统要求填写") || text.contains("如每班人数不一致，则填写最大班人数")
                    || text.contains("用3倍PC-TWA判定时") || text.contains("用5倍PC-TWA判定时")
                    || text.contains("必须填写接触频次"))setParagraphText(paragraph,"");
            if (text.replaceAll("\\s", "").equals("目录")) { paragraph.setPageBreak(true); paragraph.setKeepNext(true); }
            if (text.trim().startsWith("表") || text.trim().startsWith("附表")) paragraph.setKeepNext(true);
            if (text.contains("测结果报告单") && !text.contains("职业病危害") && !text.contains("PAGEREF")) {
                if (paragraph.getPart() == document && paragraph.getText().length() < 40) {
                    paragraph.getCTP().setPPr(org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPPr.Factory.newInstance());
                    while(!paragraph.getRuns().isEmpty())paragraph.removeRun(0);
                    paragraph.createRun().setText("检测结果报告单"); paragraph.setPageBreak(true); paragraph.setKeepNext(true);
                    paragraph.setSpacingBefore(240);paragraph.setSpacingAfter(360);paragraph.setAlignment(ParagraphAlignment.CENTER);
                    for (XWPFRun run : paragraph.getRuns()) { run.setFontSize(24); run.setBold(true); }
                }
            }
        }
        removeTocEntry(document,"附件4 用人单位职业病危害风险分级");
        for(XWPFParagraph p:new ArrayList<>(document.getParagraphs())) {
            if(p.getText().contains("本页以下空白") || p.getText().trim().equals("（非必填项）"))setParagraphText(p,"");
            if(p.getText().isBlank() && !p.getCTP().xmlText().contains("<w:drawing") && !p.getCTP().xmlText().contains("<w:pict") && !p.getCTP().xmlText().contains("fldChar")) {
                if(p.getCTP().isSetPPr() && p.getCTP().getPPr().isSetSectPr()) {
                    p.setSpacingBefore(0);p.setSpacingAfter(0);p.setSpacingBetween(0.1);
                    for(XWPFRun run:p.getRuns()){run.setFontSize(1);while(run.getCTR().sizeOfBrArray()>0)run.getCTR().removeBr(0);}
                    var sect=p.getCTP().getPPr().getSectPr();if(!sect.isSetType())sect.addNewType();sect.getType().setVal(STSectionMark.CONTINUOUS);
                } else {int pos=document.getPosOfParagraph(p);if(pos>=0)document.removeBodyElement(pos);}
            }
        }
        // All sections share one physical page count. Do not mix restarted PAGE with NUMPAGES.
        for (var footer : document.getFooterList()) {
            footer.clearHeaderFooter();
            XWPFParagraph p = footer.createParagraph();
            p.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun org = p.createRun(); org.setText(text(project,"orgName") + "　第 "); org.setFontSize(9);
            p.getCTP().addNewFldSimple().setInstr(" PAGE ");
            p.createRun().setText(" 页 / 共 "); p.getCTP().addNewFldSimple().setInstr(" NUMPAGES "); p.createRun().setText(" 页");
        }
        var sections = document.getDocument().getBody().selectPath("declare namespace w='http://schemas.openxmlformats.org/wordprocessingml/2006/main'; .//w:sectPr");
        String footerId = document.getFooterList().isEmpty() ? "" : document.getRelationId(document.getFooterList().get(0));
        String headerId = document.getHeaderList().stream().filter(h -> h.getText().contains(text(project,"projectCode"))).map(document::getRelationId).findFirst().orElse("");
        for (var element : sections) {
            var section = (org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr) element;
            if (section.isSetPgNumType()) section.unsetPgNumType();
            if (!footerId.isBlank()) {
                while (section.sizeOfFooterReferenceArray()>0) section.removeFooterReference(0);
                var ref=section.addNewFooterReference();ref.setType(org.openxmlformats.schemas.wordprocessingml.x2006.main.STHdrFtr.DEFAULT);ref.setId(footerId);
            }
            if (!headerId.isBlank() && !section.isSetTitlePg()) {
                while(section.sizeOfHeaderReferenceArray()>0)section.removeHeaderReference(0);
                var ref=section.addNewHeaderReference();ref.setType(org.openxmlformats.schemas.wordprocessingml.x2006.main.STHdrFtr.DEFAULT);ref.setId(headerId);
            }
        }
        removeDanglingTocReferences(document);
    }

    private static void removeDanglingTocReferences(XWPFDocument document) {
        String ns = "declare namespace w='http://schemas.openxmlformats.org/wordprocessingml/2006/main'; ";
        java.util.Set<String> bookmarks = new java.util.HashSet<>();
        for (var node : document.getDocument().selectPath(ns + ".//w:bookmarkStart")) {
            bookmarks.add(((org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBookmark) node).getName());
        }
        var reference = java.util.regex.Pattern.compile("PAGEREF\\s+(_Toc\\d+)");
        for (var node : document.getDocument().selectPath(ns + ".//w:p")) {
            var paragraph = (org.openxmlformats.schemas.wordprocessingml.x2006.main.CTP) node;
            var match = reference.matcher(paragraph.xmlText());
            if (!match.find() || bookmarks.contains(match.group(1))) continue;
            // The last TOC entry can also close the outer TOC field. Preserve that closure.
            int depth = 0, outerEnds = 0;
            for (var field : paragraph.selectPath(ns + ".//w:fldChar")) {
                var type = ((org.openxmlformats.schemas.wordprocessingml.x2006.main.CTFldChar) field).getFldCharType();
                if (org.openxmlformats.schemas.wordprocessingml.x2006.main.STFldCharType.BEGIN.equals(type)) depth++;
                if (org.openxmlformats.schemas.wordprocessingml.x2006.main.STFldCharType.END.equals(type)) {
                    if (depth > 0) depth--; else outerEnds++;
                }
            }
            try (var cursor = paragraph.newCursor()) {
                if (outerEnds > 0 && cursor.toPrevSibling() && cursor.getObject() instanceof org.openxmlformats.schemas.wordprocessingml.x2006.main.CTP previous) {
                    for (int i = 0; i < outerEnds; i++) previous.addNewR().addNewFldChar().setFldCharType(org.openxmlformats.schemas.wordprocessingml.x2006.main.STFldCharType.END);
                }
            }
            try (var cursor = paragraph.newCursor()) { cursor.removeXml(); }
        }
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
        insertBodyAfterHeading(document, "1.1 法律、法规与规章", text(supplement, "lawsRegulations"));
        insertBodyAfterHeading(document, "1.2 技术标准与规范", text(project, "evaluationBasis"));
        insertBodyAfterHeading(document, "1.3 基础资料", text(supplement, "basicMaterials"));
        setParagraphContaining(document, "为预防、控制和消除项目可能产生的职业病危害", text(project, "taskOrigin"));
        insertBodyAfterHeading(document, "2.3 检测范围", text(supplement, "detectionScope"));
        setParagraphContaining(document, "介绍从原材料投入到成品产出全过程的工艺描述", text(supplement, "productionProcess"));
        setParagraphContaining(document, "此表仅在进行了物料挥发性有机组分定性时需列出", text(supplement, "vocAnalysisConclusion"));

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
        setCellFontSize(table, 2, 2, 9.0);
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
        List<JsonNode> silica = all.stream()
                .filter(n -> normalizeMetric(text(n, "metricType")).contains("SILICA")
                        || text(n, "factorName").contains("游离二氧化硅"))
                .toList();
        fillTable(document, 16, 1, silica, n -> List.of(
                resultContext(n), resultContext(n), text(n, "factorName"), resultValue(n),
                fallback(text(n, "dustType"), "其他粉尘"), text(n, "jobName")));
        fillTable(document, 17, 1, filterMetric(all, "TWA", "CTWA"), n -> List.of(
                resultContext(n), text(n, "jobName"), fallback(text(n, "detectionMode"), "定点长时间检测"),
                text(n, "factorName"), resultValue(n)));
        fillTable(document, 18, 1, filterMetric(all, "STEL", "CSTE"), n -> List.of(
                resultContext(n), text(n, "jobName"), resultContext(n), text(n, "factorName"), resultValue(n)));
        fillTable(document, 19, 1, filterMetric(all, "CME", "MAC"), n -> List.of(
                resultContext(n), text(n, "jobName"), resultContext(n), text(n, "factorName"), resultValue(n)));
        fillTable(document, 20, 2, filterMetric(all, "PE", "CPE"), n -> List.of(
                resultContext(n), text(n, "jobName"), resultContext(n), text(n, "factorName"),
                resultValue(n), "-", "-", "-", multipleLimit(n, 3), "-"));
        fillTable(document, 21, 1, filterMetric(all, "LEX8H", "LEX,8H", "LEX,W"), n -> List.of(
                resultContext(n), text(n, "jobName"), "定点检测", fallback(text(n, "metricType"), "LEX,8h"), resultValue(n)));
        fillTable(document, 22, 1, filterMetric(all, "LAEQ"), n -> List.of(
                resultContext(n), text(n, "jobName"), resultContext(n), "稳态", resultValue(n)));
        fillTable(document, 40, 1, filterMetric(all, "X_GAMMA", "RADIATION_DOSE_RATE"), n -> List.of(
                resultContext(n), text(n, "jobName"), resultContext(n),
                "设备正常运行", resultValue(n)));

        fillTable(document, 45, 2, all, n -> List.of(
                resultContext(n), text(n, "jobName"), text(n, "factorName"), resultType(n),
                resultValue(n), limitType(n), effectiveLimit(n),
                fallback(text(n, "limitUnit"), text(n, "unit")), text(n, "judgement")));
        fillTable(document, 47, 1, exposureRows(nodes(positions), all), n -> List.of(
                text(n, "workLocation"), text(n, "jobName"), text(n, "factorName"), text(n, "workerCount")));

        List<JsonNode> failed = all.stream().filter(n -> "不合格".equals(text(n, "judgement"))).toList();
        fillTable(document, 48, 1, failed, n -> List.of(
                text(n, "workLocation"), text(n, "jobName"), text(n, "workLocation"), text(n, "factorName"),
                overLimitRatio(n), text(n, "metricType"), text(supplement, "overLimitSummary")));
    }

    private static void fillConclusion(XWPFDocument document, JsonNode project, JsonNode results, JsonNode supplement) {
        String risk = "根据《国家卫生健康委办公厅关于公布建设项目职业病危害风险分类管理目录的通知》"
                + "（国卫办职健发〔2021〕5号）的规定，并结合存在的危害因素及浓度、强度进行分析，"
                + text(project, "companyName") + "工作场所职业病危害风险分类为" + text(supplement, "riskCategory") + "。";
        setParagraphContaining(document, "根据《国家卫生健康委办公厅关于公布建设项目职业病危害风险分类管理目录的通知》", risk);
        List<JsonNode> all = nodes(results);
        List<JsonNode> failed = all.stream().filter(n -> "不合格".equals(text(n, "judgement"))).toList();
        long pending = all.stream().filter(n -> !"合格".equals(text(n,"judgement")) && !"不合格".equals(text(n,"judgement"))).count();
        long mixedFailures = ReportTableRepair.mixedFailures(all);
        String overLimitSummary = all.isEmpty() || pending > 0
                ? "本次" + all.size() + "项结果中，" + pending + "项无法判定，" + failed.size() + "项不合格；不能作出全部符合的结论。"
                : mixedFailures > 0 ? "本次" + all.size() + "项检测结果中，单项不合格" + failed.size() + "项；混合接触相加评价不合格" + mixedFailures + "组，详见混合接触判定表。"
                : failed.isEmpty()
                ? "本次共" + all.size() + "项检测结果，均符合相应职业接触限值或控制水平，未发现超标岗位。"
                : "本次共" + all.size() + "项检测结果，其中" + failed.size() + "项不合格，涉及"
                        + distinctJoined(failed, "jobName") + "岗位的" + distinctJoined(failed, "factorName")
                        + "，具体原因见表8.3-1。";
        setParagraphContaining(document, "明确是否存在超标岗位", overLimitSummary);
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

    private static void finalizeSummaryAndAppendices(
            XWPFDocument document, JsonNode positions, JsonNode results, JsonNode supplement,
            boolean deleteNotApplicable) {
        List<JsonNode> all = nodes(results);
        List<JsonNode> positionRows = nodes(positions);
        List<JsonNode> failed = all.stream().filter(n -> "不合格".equals(text(n, "judgement"))).toList();

        // The template offers these only when the corresponding analysis has data.
        if (deleteNotApplicable) {
            removeTableAndCaption(document, "表7.4-2 混合接触的岗位汇总及判定表");
        }
        if (deleteNotApplicable && failed.isEmpty()) {
            removeTableAndCaption(document, "表8.3-1 岗位超标原因分析表");
            removeParagraphContaining(document, "物理因素、放射性因素、混合接触超标的不需要写超标倍数");
        }

        fillAppendixExposureSummary(document, positionRows, all);
        fillAppendixDetectionSummary(document, all);
        fillAppendixLimitTables(document, all, deleteNotApplicable);

        // All currently supported physical examples are noise measurements. Do not retain unrelated
        // heat, electromagnetic-field, microwave, ultraviolet, laser or vibration limit pages.
        if (deleteNotApplicable) {
            removeTableAndCaption(document, "附表1.4-2 工作场所脉冲噪声职业接触限值");
            removeBodyRange(document, "1.4.2 高温职业接触限值", "1.5 放射性因素职业接触限值");
        }

        boolean hasRadiation = !filterMetric(all, "X_GAMMA", "RADIATION_DOSE_RATE").isEmpty()
                && "INCLUDED_REPORT".equals(normalizeMetric(text(supplement, "radiationSourceMode")));
        if (hasRadiation) {
            setParagraphContaining(document, "根据各类放射性因素的实际情况填写",
                    "本项目X射线探伤场所周围剂量当量率按2.5 μSv/h控制水平进行判定，执行GBZ 117—2022。" );
        } else if (deleteNotApplicable) {
            removeBodyRange(document, "1.5 放射性因素职业接触限值", "1.6 工作场所物理因素、放射性因素职业接触控制要求");
            setParagraphContaining(document, "1.6 工作场所物理因素、放射性因素职业接触控制要求",
                    "1.5 工作场所物理因素职业接触控制要求");
            setParagraphContaining(document, "同一检测岗位用职业病危害因素接触水平（放射性因素为检测结果）最高的结果进行判定",
                    "同一检测岗位采用职业病危害因素接触水平最高的结果进行判定。当任一职业病危害因素接触水平超过相应职业接触限值时，该检测岗位判定为不符合卫生要求。");
        }

        // Appendix 4, Appendix 5 and other attachments are explicitly optional in the source template.
        // They are removed until real grading material or photos are supplied.
        boolean hasPhotos = supplement.path("reportPhotos").isArray() && supplement.path("reportPhotos").size() > 0;
        if (deleteNotApplicable && hasPhotos) {
            removeBodyRange(document, "附件4 用人单位职业病危害风险分级", "附件5 合影");
            removeBodyRange(document, "附件（其他）", "检检测结果报告单");
            removeTocEntry(document, "附件4 用人单位职业病危害风险分级");
            removeTocEntry(document, "附件（其他）");
        } else if (deleteNotApplicable) {
            removeBodyRange(document, "附件4 用人单位职业病危害风险分级", "检检测结果报告单");
            removeTocEntry(document, "附件4 用人单位职业病危害风险分级");
            removeTocEntry(document, "附件5 合影");
            removeTocEntry(document, "附件（其他）");
        }
    }

    private static void fillAppendixLimitTables(
            XWPFDocument document, List<JsonNode> results, boolean deleteNotApplicable) {
        Map<String, List<JsonNode>> byFactor = new LinkedHashMap<>();
        for (JsonNode result : results) {
            if ("物理因素".equals(categoryForResult(result)) || isRadiationMetric(text(result, "metricType"))
                    || normalizeMetric(text(result, "metricType")).contains("SILICA")) {
                continue;
            }
            byFactor.computeIfAbsent(text(result, "factorName"), key -> new ArrayList<>()).add(result);
        }
        List<JsonNode> chemicals = new ArrayList<>();
        List<JsonNode> dusts = new ArrayList<>();
        for (Map.Entry<String, List<JsonNode>> entry : byFactor.entrySet()) {
            List<JsonNode> factorResults = entry.getValue();
            if (isDustFactor(entry.getKey())) {
                dusts.add(object("factor", entry.getKey(), "pcTwa", firstLimit(factorResults, "TWA", "CTWA", "PE", "CPE")));
            } else {
                chemicals.add(object("factor", entry.getKey(),
                        "mac", firstLimit(factorResults, "CME", "MAC"),
                        "pcTwa", firstLimit(factorResults, "TWA", "CTWA", "PE", "CPE"),
                        "pcStel", firstLimit(factorResults, "STEL", "CSTE")));
            }
        }
        if (chemicals.isEmpty() && deleteNotApplicable) {
            removeTableAndCaption(document, "附表1.1-1 工作场所空气中化学有害因素职业接触限值");
        } else if (!chemicals.isEmpty()) {
            fillTable(tableContainingAll(document, "职业接触限值OELs", "PC-STEL"), 2, chemicals, n -> List.of(
                    text(n, "factor"), "/", "/", fallback(text(n, "mac"), "/"),
                    fallback(text(n, "pcTwa"), "/"), fallback(text(n, "pcStel"), "/"), "/", "/"));
        }
        if (dusts.isEmpty() && deleteNotApplicable) {
            removeTableAndCaption(document, "附表1.1-2 工作场所空气中粉尘职业接触限值");
        } else if (!dusts.isEmpty()) {
            fillTable(tableContainingAll(document, "粉尘类型", "呼吸性粉尘", "PC-TWA"), 2, dusts, n -> List.of(
                    text(n, "factor"), "/", "/", fallback(text(n, "pcTwa"), "/"), "/", "/", "/"));
        }
    }

    private static void fillAppendixExposureSummary(
            XWPFDocument document, List<JsonNode> positions, List<JsonNode> results) {
        XWPFTable table = tableContainingAll(document, "接触人数", "需申报的具体职业病危害因素");
        Map<String, List<JsonNode>> categories = categorizeResults(results);
        Map<String, int[]> rows = appendixCategoryRows();
        for (Map.Entry<String, int[]> entry : rows.entrySet()) {
            String category = entry.getKey();
            List<JsonNode> categoryResults = categories.getOrDefault(category, List.of());
            Map<String, Integer> categoryJobs = uniqueJobWorkers(categoryResults, positions);
            Map<String, Map<String, Integer>> factorJobs = new LinkedHashMap<>();
            for (JsonNode result : categoryResults) {
                factorJobs.computeIfAbsent(displayHazardFactor(result), key -> new LinkedHashMap<>())
                        .putIfAbsent(jobKey(result), workerCountFor(result, positions));
            }
            int[] targetRows = entry.getValue();
            setCell(table, targetRows[0], 2, String.valueOf(sumWorkers(categoryJobs)));
            writeFactorWorkerRows(table, targetRows, factorJobs, 3, 4);
        }
    }

    private static void fillAppendixDetectionSummary(XWPFDocument document, List<JsonNode> results) {
        XWPFTable table = tableContainingAll(document, "检测点数", "需申报的具体职业病危害因素");
        Map<String, List<JsonNode>> categories = categorizeResults(results);
        Map<String, int[]> rows = appendixCategoryRows();
        for (Map.Entry<String, int[]> entry : rows.entrySet()) {
            List<JsonNode> categoryResults = categories.getOrDefault(entry.getKey(), List.of());
            int[] targetRows = entry.getValue();
            setCell(table, targetRows[0], 2, String.valueOf(categoryResults.size()));
            setCell(table, targetRows[0], 3, String.valueOf(categoryResults.stream()
                    .filter(n -> "不合格".equals(text(n, "judgement"))).count()));

            Map<String, List<JsonNode>> factors = new LinkedHashMap<>();
            for (JsonNode result : categoryResults) {
                factors.computeIfAbsent(displayHazardFactor(result), key -> new ArrayList<>()).add(result);
            }
            int rowOffset = 0;
            for (Map.Entry<String, List<JsonNode>> factor : factors.entrySet()) {
                int row = targetRows[Math.min(rowOffset, targetRows.length - 1)];
                String factorName = rowOffset < targetRows.length - 1
                        ? factor.getKey()
                        : joinRemainingKeys(factors, rowOffset);
                List<JsonNode> factorResults = factor.getValue();
                setCell(table, row, 4, factorName);
                setCell(table, row, 5, String.valueOf(factorResults.size()));
                setCell(table, row, 6, String.valueOf(factorResults.stream()
                        .filter(n -> "不合格".equals(text(n, "judgement"))).count()));
                if (++rowOffset >= targetRows.length) {
                    break;
                }
            }
            if (factors.isEmpty()) {
                setCell(table, targetRows[0], 4, "—");
                setCell(table, targetRows[0], 5, "0");
                setCell(table, targetRows[0], 6, "0");
            }
        }
    }

    private static Map<String, List<JsonNode>> categorizeResults(List<JsonNode> results) {
        Map<String, List<JsonNode>> categories = new LinkedHashMap<>();
        for (String category : appendixCategoryRows().keySet()) {
            categories.put(category, new ArrayList<>());
        }
        for (JsonNode result : results) {
            categories.get(categoryForResult(result)).add(result);
        }
        return categories;
    }

    private static Map<String, int[]> appendixCategoryRows() {
        Map<String, int[]> rows = new LinkedHashMap<>();
        rows.put("粉尘", new int[] {3, 4});
        rows.put("化学物质", new int[] {5, 6});
        rows.put("物理因素", new int[] {7, 8});
        rows.put("放射性因素", new int[] {9});
        rows.put("生物因素", new int[] {10});
        rows.put("其他", new int[] {11});
        return rows;
    }

    private static void writeFactorWorkerRows(
            XWPFTable table, int[] rows, Map<String, Map<String, Integer>> factorJobs, int nameColumn, int countColumn) {
        int offset = 0;
        for (Map.Entry<String, Map<String, Integer>> factor : factorJobs.entrySet()) {
            int row = rows[Math.min(offset, rows.length - 1)];
            String name = offset < rows.length - 1 ? factor.getKey() : joinRemainingKeys(factorJobs, offset);
            setCell(table, row, nameColumn, name);
            setCell(table, row, countColumn, String.valueOf(sumWorkers(factor.getValue())));
            if (++offset >= rows.length) {
                break;
            }
        }
        if (factorJobs.isEmpty()) {
            setCell(table, rows[0], nameColumn, "—");
            setCell(table, rows[0], countColumn, "0");
        }
    }

    private static <T> String joinRemainingKeys(Map<String, T> values, int fromIndex) {
        return values.keySet().stream().skip(fromIndex).reduce((a, b) -> a + "、" + b).orElse("—");
    }

    private static Map<String, Integer> uniqueJobWorkers(List<JsonNode> results, List<JsonNode> positions) {
        Map<String, Integer> workers = new LinkedHashMap<>();
        for (JsonNode result : results) {
            workers.putIfAbsent(jobKey(result), workerCountFor(result, positions));
        }
        return workers;
    }

    private static int sumWorkers(Map<String, Integer> workers) {
        return workers.values().stream().mapToInt(Integer::intValue).sum();
    }

    private static int workerCountFor(JsonNode result, List<JsonNode> positions) {
        for (JsonNode position : positions) {
            if (text(position, "workLocation").equals(text(result, "workLocation"))
                    && text(position, "jobName").equals(text(result, "jobName"))) {
                return position.path("workerCount").asInt(0);
            }
        }
        return 0;
    }

    private static String jobKey(JsonNode result) {
        return text(result, "workLocation") + "\u0000" + text(result, "jobName");
    }

    private static String categoryForResult(JsonNode result) {
        return ReportResultRules.category(text(result, "factorName"), text(result, "metricType"), text(result, "factorCategory"));
    }

    private static boolean isDustFactor(String factorName) {
        return factorName != null && (factorName.contains("粉尘") || factorName.contains("烟尘")
                || factorName.contains("矽尘") || factorName.contains("水泥尘"));
    }

    private static String displayHazardFactor(JsonNode result) {
        String factor = text(result, "factorName");
        if (isRadiationMetric(text(result, "metricType")) && factor.contains("X射线")) {
            return "X射线";
        }
        if (normalizeMetric(text(result, "metricType")).contains("SILICA")) {
            return "粉尘（游离二氧化硅）";
        }
        return factor;
    }

    private static String firstLimit(List<JsonNode> results, String... metrics) {
        List<JsonNode> matches = filterMetric(results, metrics);
        return matches.isEmpty() ? "" : decimalText(matches.get(0), "limitValue");
    }

    private static void finalizeVocAnalysisSection(
            XWPFDocument document, JsonNode supplement, boolean deleteNotApplicable) {
        List<JsonNode> items = nodes(supplement.path("vocAnalysisItems"));
        if (supplement.path("vocAnalysisPerformed").asBoolean(false)) {
            fillTable(document, 6, 1, items, n -> List.of(
                    text(n, "chemicalName"), text(n, "manufacturerBatchNo"), text(n, "detectedComponents")));
            return;
        }
        if (!deleteNotApplicable) {
            fillTable(document, 6, 1, List.of(object("notApplicable", "不涉及")),
                    n -> List.of("不涉及", "/", "/"));
            return;
        }
        XWPFTable conditionalTable = table(document, 6);
        if (conditionalTable != null) {
            int position = document.getPosOfTable(conditionalTable);
            if (position >= 0) {
                document.removeBodyElement(position);
            }
        }
        removeParagraphContaining(document, "表3.2-2 物料挥发性有机组分定性结果表");
    }

    private static void finalizeRadiationSourceSection(
            XWPFDocument document, JsonNode project, JsonNode supplement, JsonNode results,
            boolean deleteNotApplicable) {
        List<JsonNode> xrayDevices = nodes(supplement.path("xrayDevices"));
        List<JsonNode> sealedSources = nodes(supplement.path("sealedSources"));
        List<JsonNode> unsealedSources = nodes(supplement.path("unsealedSources"));
        fillTable(document, 7, 1, xrayDevices, n -> List.of(
                text(n, "serialNo"), text(n, "workplace"), text(n, "deviceName"), text(n, "category"),
                text(n, "model"), text(n, "manufacturer"), text(n, "mainParameters"), text(n, "radiationType")));
        fillTable(document, 8, 1, sealedSources, n -> List.of(
                text(n, "serialNo"), text(n, "workplace"), text(n, "nuclideName"), text(n, "code"),
                text(n, "factoryActivity"), text(n, "category"), text(n, "installationLocation"), text(n, "purpose")));
        fillTable(document, 9, 1, unsealedSources, n -> List.of(
                text(n, "workplace"), text(n, "nuclideName"), text(n, "dailyMaxActivity"),
                text(n, "dailyEquivalentMaxActivity"), text(n, "annualMaxActivity"),
                text(n, "physicochemicalProperty"), text(n, "operationMode"), text(n, "storageMethodLocation")));

        String mode = normalizeMetric(text(supplement, "radiationSourceMode"));
        if (mode.isBlank()) {
            mode = "NONE";
        }
        String summary = text(supplement, "radiationSourceSummary");
        String selected = switch (mode) {
            case "EXCLUDED_CONTRACT" -> fallback(summary,
                    "用人单位存在辐射源项。本次采用第①种方式：根据合同约定，放射性因素不在本次定期检测范围内，另行委托有资质机构检测。");
            case "SEPARATE_REPORT" -> fallback(summary,
                    "用人单位存在辐射源项。本次采用第②种方式：放射性因素检测结果由合作机构另行出具报告（"
                            + fallback(text(supplement, "radiationSeparateReportNo"), "报告编号待填写") + "）。");
            case "INCLUDED_REPORT" -> fallback(summary,
                    "用人单位存在射线装置。本次采用第③种方式：将放射性因素及检测结果列入本报告，检测主体以各结果单所载实际检测机构为准。");
            default -> fallback(summary, "用人单位不存在辐射源项。");
        };
        setParagraphContaining(document, "无辐射源项时：用人单位不存在辐射源项", selected);
        removeParagraphContaining(document, "有辐射源项时（根据实际情况选择其中一条）");
        removeParagraphContaining(document, "根据实际情况在以下内容三选一");
        removeParagraphContaining(document, "①根据合同约定");
        removeParagraphContaining(document, "②（与核辐射所一起合作");
        removeParagraphContaining(document, "③（与核辐射所一起合作");

        if (deleteNotApplicable && (!"INCLUDED_REPORT".equals(mode) || xrayDevices.isEmpty())) {
            removeTableAndCaption(document, "表3.3-1 射线装置情况表");
        }
        if (deleteNotApplicable && (!"INCLUDED_REPORT".equals(mode) || sealedSources.isEmpty())) {
            removeTableAndCaption(document, "表3.3-2 密封源情况表");
        }
        if (deleteNotApplicable && (!"INCLUDED_REPORT".equals(mode) || unsealedSources.isEmpty())) {
            removeTableAndCaption(document, "表3.3-3 非密封源情况表");
        }

    }

    private static void finalizeExclusionScreeningSection(
            XWPFDocument document, JsonNode supplement, boolean deleteNotApplicable) {
        if (supplement.path("exclusionScreeningPerformed").asBoolean(false)) {
            fillTable(document, 14, 1, nodes(supplement.path("exclusionScreeningItems")), n -> List.of(
                    text(n, "workplace"), text(n, "jobName"), text(n, "factorName"), text(n, "ctwa"),
                    text(n, "pcTwa"), text(n, "contactJudgement")));
            return;
        }
        if (deleteNotApplicable) {
            removeBodyRange(document, "4.2 排除性检测及接触判定", "5 检测项目");
            removeTocEntry(document, "4.2 排除性检测及接触判定");
        } else {
            fillTable(document, 14, 1, List.of(object("notApplicable", "不涉及")),
                    n -> List.of("不涉及", "/", "/", "/", "/", "/"));
        }
    }

    private static void finalizeResultSections(
            XWPFDocument document, JsonNode results, JsonNode supplement, boolean deleteNotApplicable) {
        if (!deleteNotApplicable) {
            return;
        }
        List<JsonNode> all = nodes(results);
        boolean hasSilica = all.stream()
                .anyMatch(n -> normalizeMetric(text(n, "metricType")).contains("SILICA")
                        || text(n, "factorName").contains("游离二氧化硅"));
        boolean hasTwa = !filterMetric(all, "TWA", "CTWA").isEmpty();
        boolean hasSte = !filterMetric(all, "STEL", "CSTE").isEmpty();
        boolean hasMe = !filterMetric(all, "CME", "MAC").isEmpty();
        boolean hasPe = !filterMetric(all, "PE", "CPE").isEmpty();
        boolean hasLex = !filterMetric(all, "LEX8H", "LEX,8H", "LEX,W").isEmpty();
        boolean hasLaeq = !filterMetric(all, "LAEQ").isEmpty();
        boolean hasRadiation = !filterMetric(all, "X_GAMMA", "RADIATION_DOSE_RATE").isEmpty();

        if (!hasSilica) {
            removeResultSubsection(document, "7.1.1 粉尘中游离二氧化硅含量", "表7.1-1 粉尘中游离二氧化硅含量结果表",
                    "该项目所采集样品均为上述采样地点的落尘");
            removeBodyRange(document, "游离二氧化硅含量", "总粉尘/呼吸性粉尘/化学有害因素（化学因素写实际名称）");
        }
        if (!hasTwa) {
            removeResultSubsection(document, "7.1.2 时间加权平均接触浓度（CTWA）", "表7.1-2 CTWA结果表",
                    "岗位（工种）名称后的带圈数字为采样对象序号");
        }
        if (!hasSte) {
            removeResultSubsection(document, "7.1.3 短时间接触浓度（CSTE）", "表7.1-3 CSTE结果表", null);
        }
        if (!hasMe) {
            removeResultSubsection(document, "7.1.4 最高接触浓度（CME）", "表7.1-4 CME结果表", null);
        }
        if (!hasPe) {
            removeResultSubsection(document, "7.1.5 峰接触浓度（CPE）", "表7.1-5 CPE结果表",
                    "用3倍PC-TWA判定时");
        }
        if (!hasTwa && !hasSte && !hasMe && !hasPe && !hasSilica) {
            removeParagraphExact(document, "7.1 化学有害因素（含粉尘）");
            removeTocEntry(document, "7.1 化学有害因素（含粉尘）");
        }
        removeTableAndCaption(document, "检测结果（长时间采样）");
        if (!hasSte && !hasMe && !hasPe) {
            removeTableAndCaption(document, "检测结果（短时间采样）");
            removeParagraphContaining(document, "直读仪器测定填写测量时间");
        }

        if (!hasLaeq) {
            removeResultSubsection(document, "7.2.1.2 工作地点噪声声级", "表7.2-2 工作地点噪声声级结果表", null);
            removeTableAndCaption(document, "检测结果（定点噪声测量）");
            removeParagraphContaining(document, "LAeq默认测量时长为15s");
        }
        removeResultSubsection(document, "7.2.1.3 工作地点脉冲噪声声级", "表7.2-3 工作地点脉冲噪声声级结果表", null);
        removeTableAndCaption(document, "检测结果（脉冲噪声测量）");
        if (!hasLex) {
            removeResultSubsection(document, "7.2.1.1 8h/40h等效声级（LEX,8h/LEX,W）", "表7.2-1 LEX,8h/LEX,W结果表", null);
            removeTableAndCaption(document, "检测结果（个体噪声测量）");
        }
        if (!hasLex && !hasLaeq) {
            removeParagraphExact(document, "7.2.1 噪声");
        }
        removeBodyRange(document, "7.2.2 高温WBGT", "7.3 放射性因素");
        if (!hasLex && !hasLaeq) {
            removeParagraphExact(document, "7.2 物理因素");
            removeTocEntry(document, "7.2 物理因素");
        }

        removeBodyRange(document, "高温", "X、γ、中子射线");

        String radiationMode = normalizeMetric(text(supplement, "radiationSourceMode"));
        if (!"INCLUDED_REPORT".equals(radiationMode) || !hasRadiation) {
            removeBodyRange(document, "7.3 放射性因素", "7.4 岗位检测结果汇总及判定");
            removeTocEntry(document, "7.3 放射性因素");
            removeBodyRange(document, "X、γ、中子射线", "有机化学品成分定性分析");
        } else {
            removeResultSubsection(document, "7.3.2 α、β表面污染", "表7.3-2 α、β表面污染结果表", null);
            removeResultSubsection(document, "7.3.3 密封源泄漏", "表7.3-3 密封源泄漏结果表", null);
            removeResultSubsection(document, "7.3.4 空气中放射性核素", "表7.3-4 空气中放射性核素结果表", null);
            removeResultSubsection(document, "7.3.5 氡及其子体浓度", "表7.3-5 氡及其子体浓度结果表", null);
            removeBodyRange(document, "α、β表面污染", "有机化学品成分定性分析");
        }
        if (!supplement.path("vocAnalysisPerformed").asBoolean(false)) {
            removeBodyRangeToEnd(document, "有机化学品成分定性分析");
        }
    }

    private static void fillResultDetailSheets(
            XWPFDocument document, JsonNode project, JsonNode results, JsonNode methods, JsonNode supplement) {
        if (text(supplement, "qualificationCertificateImageDataUrl").isBlank()) {
            setParagraphContaining(document, "资质证书", fallback(
                    text(supplement, "qualificationCertificatePlan"),
                    "资质认定证书由报告签发机构按受控版本随正式报告提供。"));
        }

        List<JsonNode> chemicalResults = nodes(results).stream()
                .filter(n -> !isNoiseMetric(text(n, "metricType")) && !isRadiationMetric(text(n, "metricType")))
                .toList();
        List<JsonNode> noiseResults = nodes(results).stream()
                .filter(n -> isNoiseMetric(text(n, "metricType")))
                .toList();
        List<JsonNode> radiationResults = filterMetric(nodes(results), "X_GAMMA", "RADIATION_DOSE_RATE");

        List<JsonNode> silicaResults = nodes(results).stream()
                .filter(n -> normalizeMetric(text(n, "metricType")).contains("SILICA")
                        || text(n, "factorName").contains("游离二氧化硅"))
                .toList();
        if (!silicaResults.isEmpty()) {
            setCell(table(document, 69), 1, 2, "工作场所空气中粉尘游离二氧化硅含量");
            setCell(table(document, 69), 2, 2, text(project, "samplingDate"));
            setCell(table(document, 69), 3, 2, text(project, "reportDate"));
            setCell(table(document, 69), 4, 2, "GBZ/T 192.4—2007");
            setCell(table(document, 69), 5, 2, "焦磷酸法");
            setCell(table(document, 69), 6, 2, "分析天平/恒温干燥箱");
            fillTable(document, 70, 1, silicaResults, n -> List.of(
                    "S-" + paddedSequence(n), "C-" + paddedSequence(n), text(n, "workLocation"),
                    text(n, "workLocation"), text(n, "factorName"), resultValue(n)));
        }

        List<JsonNode> chemicalMethods = nodes(methods.path("chemical"));
        String chemicalFactors = distinctJoined(chemicalResults, "factorName");
        String chemicalBasis = fallbackDetail(
                joinedField(chemicalMethods, "basis"), defaultChemicalDetail(chemicalResults, "basis"));
        String chemicalMethod = fallbackDetail(
                joinedField(chemicalMethods, "method"), defaultChemicalDetail(chemicalResults, "method"));
        String chemicalInstruments = fallbackDetail(
                joinedField(chemicalMethods, "instruments"), defaultChemicalDetail(chemicalResults, "instruments"));
        setCell(table(document, 71), 1, 2, chemicalFactors.isBlank()
                ? "本项目不涉及化学有害因素检测"
                : "工作场所空气中" + chemicalFactors + "浓度");
        setCell(table(document, 71), 2, 2, text(project, "samplingDate"));
        setCell(table(document, 71), 3, 2, text(project, "reportDate"));
        setCell(table(document, 71), 4, 2, chemicalBasis);
        setCell(table(document, 71), 5, 2, chemicalMethod);
        setCell(table(document, 71), 6, 2, chemicalInstruments);
        setCell(table(document, 71), 7, 3, "/");
        setCell(table(document, 71), 8, 3, "/");
        fillChemicalDetailTable(document, 72, filterMetric(chemicalResults, "TWA", "CTWA"), false);
        fillChemicalDetailTable(document, 74,
                filterMetric(chemicalResults, "STEL", "CSTE", "PE", "CPE", "CME", "MAC"), true);

        List<JsonNode> physicalMethods = nodes(methods.path("physical"));
        setCell(table(document, 75), 2, 2, text(project, "samplingDate"));
        setCell(table(document, 75), 3, 2, joinedField(physicalMethods, "basis"));
        setCell(table(document, 75), 4, 2, fallback(joinedField(physicalMethods, "method"), "仪器直读法"));
        setCell(table(document, 75), 5, 2, joinedField(physicalMethods, "instruments"));
        fillNoiseDetailTable(document, 76, filterMetric(noiseResults, "LEX8H", "LEX,8H", "LEX,W"), false);
        fillNoiseDetailTable(document, 77, filterMetric(noiseResults, "LAEQ"), true);
        fillTable(document, 78, 1, List.of(object("notApplicable", "不涉及")),
                n -> List.of(text(n, "notApplicable"), "/", "/", "/", "/", "/", "/"));

        // 模板包含全部物理/放射因素的备用记录单；本项目仅涉及噪声时，其他表单显式标记“不涉及”。
        int[] unusedInfoTables = {79, 82, 86, 89, 91, 93, 95, 97, 101, 103, 105};
        for (int index : unusedInfoTables) {
            setCell(table(document, index), 1, 2, "本项目不涉及");
            XWPFTable info = table(document, index);
            if (info != null) {
                for (int row = 2; row <= info.getNumberOfRows(); row++) {
                    setCell(info, row, 2, "/");
                }
            }
        }
        int[] unusedDataTables = {80, 81, 83, 84, 85, 87, 88, 90, 92, 94, 96, 98, 102, 104, 106};
        for (int index : unusedDataTables) {
            XWPFTable data = table(document, index);
            if (data == null) {
                continue;
            }
            List<String> values = new ArrayList<>();
            for (int column = 0; column < data.getRow(0).getTableCells().size(); column++) {
                values.add(column == 0 ? "不涉及" : "/");
            }
            fillTable(document, index, index == 81 || index == 87 || index == 88 || index == 90
                    || index == 92 || index == 94 || index == 98 || index == 102 ? 2 : 1,
                    List.of(object("notApplicable", "不涉及")), n -> values);
        }

        if (radiationResults.isEmpty()) {
            setCell(table(document, 99), 1, 2, "本项目不涉及");
            fillTable(document, 100, 1, List.of(object("notApplicable", "不涉及")),
                    n -> List.of("不涉及", "/", "/", "/", "/"));
        } else {
            setCell(table(document, 99), 1, 2, "工作场所X射线周围剂量当量率");
            setCell(table(document, 99), 2, 2, text(project, "samplingDate"));
            setCell(table(document, 99), 3, 2, "GBZ 117—2022、GBZ/T 189.1—2007");
            setCell(table(document, 99), 4, 2, "X-γ辐射剂量率仪（RAD-01）");
            fillTable(document, 100, 1, radiationResults, n -> List.of(
                    "R-" + paddedSequence(n), "设备正常运行", text(n, "workLocation"), resultValue(n),
                    fallback(text(n, "judgement"), "/")));
        }

        if (supplement.path("vocAnalysisPerformed").asBoolean(false)) {
            setCell(table(document, 107), 1, 2, "挥发性有机组分定性分析和峰面积百分比测定");
            setCell(table(document, 107), 2, 2, text(supplement, "vocSamplingDate"));
            setCell(table(document, 107), 3, 2, text(supplement, "vocDetectionDate"));
            setCell(table(document, 107), 4, 2, text(supplement, "vocDetectionBasis"));
            setCell(table(document, 107), 5, 2, text(supplement, "vocDetectionMethod"));
            setCell(table(document, 107), 6, 2, text(supplement, "vocInstrument"));
            setCell(table(document, 108), 1, 2, text(supplement, "vocTotalPeakAreaPercent"));
            setCell(table(document, 108), 1, 4, text(supplement, "vocInorganicPeakAreaPercent"));
            fillTable(document, 108, 2, nodes(supplement.path("vocAnalysisItems")), n -> List.of(
                    text(n, "detectedComponents"), text(n, "casNo"), "", text(n, "peakAreaPercent"), ""));
        }
    }

    private static void fillChemicalDetailTable(XWPFDocument document, int tableIndex, List<JsonNode> items,
                                                 boolean includesLocation) {
        if (items.isEmpty()) {
            fillTable(document, tableIndex, 1, List.of(object("notApplicable", "不涉及")),
                    n -> includesLocation
                            ? List.of(text(n, "notApplicable"), "/", "/", "/", "/", "/", "/", "/")
                            : List.of(text(n, "notApplicable"), "/", "/", "/", "/", "/", "/"));
            return;
        }
        fillTable(document, tableIndex, 1, items, n -> {
            String sequence = paddedSequence(n);
            if (includesLocation) {
                return List.of("S-" + sequence, "C-" + sequence, text(n, "workLocation"), text(n, "jobName"),
                        text(n, "workLocation"), "见采样记录", "见采样记录", resultValue(n));
            }
            return List.of("S-" + sequence, "C-" + sequence, text(n, "workLocation"), text(n, "jobName"),
                    "见采样记录", "见采样记录", resultValue(n));
        });
    }

    private static void fillNoiseDetailTable(XWPFDocument document, int tableIndex, List<JsonNode> items,
                                              boolean includesLocation) {
        if (items.isEmpty()) {
            fillTable(document, tableIndex, 1, List.of(object("notApplicable", "不涉及")),
                    n -> includesLocation
                            ? List.of(text(n, "notApplicable"), "/", "/", "/", "/", "/", "/")
                            : List.of(text(n, "notApplicable"), "/", "/", "/", "/", "/"));
            return;
        }
        fillTable(document, tableIndex, 1, items, n -> {
            String sequence = "N-" + paddedSequence(n);
            if (includesLocation) {
                return List.of(sequence, text(n, "workLocation"), text(n, "jobName"), text(n, "workLocation"),
                        "见测量记录", "见测量记录", resultValue(n));
            }
            return List.of(sequence, text(n, "workLocation"), text(n, "jobName"),
                    "见测量记录", "见测量记录", resultValue(n));
        });
    }

    private static boolean isNoiseMetric(String metricType) {
        String metric = normalizeMetric(metricType);
        return "LEX8H".equals(metric) || "LEX,8H".equals(metric) || "LEX,W".equals(metric) || "LAEQ".equals(metric);
    }

    private static boolean isRadiationMetric(String metricType) {
        String metric = normalizeMetric(metricType);
        return "X_GAMMA".equals(metric) || "RADIATION_DOSE_RATE".equals(metric)
                || "SURFACE_CONTAMINATION".equals(metric) || "SEALED_SOURCE_LEAKAGE".equals(metric)
                || "AIR_RADIONUCLIDE".equals(metric) || "RADON".equals(metric);
    }

    private static String paddedSequence(JsonNode node) {
        int sequence = node.path("seq").asInt(1);
        return String.format(Locale.ROOT, "%03d", sequence);
    }

    private static String joinedField(List<JsonNode> nodes, String field) {
        List<String> values = new ArrayList<>();
        for (JsonNode node : nodes) {
            String value = text(node, field);
            if (!value.isBlank() && !values.contains(value)) {
                values.add(value);
            }
        }
        return values.isEmpty() ? "/" : String.join("；", values);
    }

    private static String defaultChemicalDetail(List<JsonNode> results, String field) {
        if (results.isEmpty()) {
            return "/";
        }
        boolean hasDust = results.stream().anyMatch(n -> isDustFactor(text(n, "factorName")));
        boolean hasOtherChemical = results.stream().anyMatch(n -> !isDustFactor(text(n, "factorName"))
                && !normalizeMetric(text(n, "metricType")).contains("SILICA"));
        List<String> values = new ArrayList<>();
        if (hasDust) {
            values.add(switch (field) {
                case "basis" -> "GBZ/T 192.1—2007 工作场所空气中粉尘测定 第1部分：总粉尘浓度";
                case "method" -> "滤膜称量法";
                case "instruments" -> "粉尘采样器 FC-01、电子天平 BAL-01";
                default -> "/";
            });
        }
        if (hasOtherChemical) {
            values.add(switch (field) {
                case "basis" -> "GBZ/T 160系列—2004/2007 工作场所空气有毒物质测定";
                case "method" -> "按相应职业卫生标准规定的采样与分析方法";
                case "instruments" -> "空气采样器 FC-02、气相色谱仪 GC-01";
                default -> "/";
            });
        }
        return values.isEmpty() ? "/" : String.join("；", values);
    }

    private static String fallbackDetail(String value, String fallback) {
        return value == null || value.isBlank() || "/".equals(value.trim()) ? fallback : value;
    }

    private static String distinctJoined(List<JsonNode> nodes, String field) {
        List<String> values = new ArrayList<>();
        for (JsonNode node : nodes) {
            String value = text(node, field);
            if (!value.isBlank() && !values.contains(value)) {
                values.add(value);
            }
        }
        return String.join("、", values);
    }

    private static void removeStandaloneConversionArtifacts(XWPFDocument document) {
        for (XWPFParagraph paragraph : allParagraphs(document)) {
            if ("t".equals(paragraph.getText().trim())) {
                setParagraphText(paragraph, "");
            }
        }
    }

    private static void replaceHeaders(XWPFDocument document, String headerName, String projectCode) {
        for (XWPFHeader header : document.getHeaderList()) {
            for (XWPFParagraph paragraph : header.getParagraphs()) {
                String current = paragraph.getText();
                String reportTitle;
                if (current.contains("职业病危害因素检测结果报告单")) {
                    reportTitle = "职业病危害因素检测结果报告单";
                } else if (current.contains("职业病危害因素检测报告")) {
                    reportTitle = "职业病危害因素检测报告";
                } else {
                    continue;
                }
                formatHeader(paragraph, fallback(headerName, "用人单位"), reportTitle, projectCode);
            }
        }
    }

    /**
     * Preserves the source template's one-line header: company/report title on the left and report
     * number on the right. A right-aligned tab replaces the template's fragile placeholder spaces,
     * while fit-text keeps a long company name inside the original left-side width without wrapping.
     */
    private static void formatHeader(
            XWPFParagraph paragraph, String headerName, String reportTitle, String projectCode) {
        while (!paragraph.getRuns().isEmpty()) {
            paragraph.removeRun(0);
        }
        paragraph.setAlignment(ParagraphAlignment.LEFT);
        paragraph.setSpacingBefore(0);
        paragraph.setSpacingAfter(0);

        String firstLine = headerName.trim() + "　" + reportTitle;
        XWPFRun titleRun = paragraph.createRun();
        titleRun.setText(firstLine);
        titleRun.setColor("000000");
        titleRun.setUnderline(UnderlinePatterns.SINGLE);
        titleRun.setFontSize(9.0);
        var titleProperties = titleRun.getCTR().isSetRPr()
                ? titleRun.getCTR().getRPr()
                : titleRun.getCTR().addNewRPr();
        var fitText = titleProperties.addNewFitText();
        fitText.setVal(BigInteger.valueOf(5900));
        fitText.setId(BigInteger.ONE);

        XWPFRun tabRun = paragraph.createRun();
        tabRun.addTab();
        tabRun.setColor("000000");
        tabRun.setUnderline(UnderlinePatterns.SINGLE);
        tabRun.setFontSize(9.0);

        XWPFRun codeRun = paragraph.createRun();
        codeRun.setText(fallback(projectCode, "/"));
        codeRun.setColor("000000");
        codeRun.setUnderline(UnderlinePatterns.SINGLE);
        codeRun.setFontSize(9.0);
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
        for (var footer : document.getFooterList()) {
            for (XWPFParagraph paragraph : footer.getParagraphs()) replaceParagraphText(paragraph, needle, replacement);
            for (XWPFTable table : footer.getTables()) for (XWPFTableRow row : table.getRows())
                for (XWPFTableCell cell : row.getTableCells()) for (XWPFParagraph paragraph : cell.getParagraphs()) replaceParagraphText(paragraph, needle, replacement);
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
            if (!paragraphs.get(i).getText().trim().equals(heading)) {
                continue;
            }
            if (i + 1 < paragraphs.size()) {
                setParagraphText(paragraphs.get(i + 1), body);
                return;
            }
        }
    }

    private static boolean removeParagraphContaining(XWPFDocument document, String needle) {
        if (needle == null || needle.isBlank()) {
            return false;
        }
        for (XWPFParagraph paragraph : new ArrayList<>(document.getParagraphs())) {
            if (paragraph.getText().contains(needle)) {
                int position = document.getPosOfParagraph(paragraph);
                return position >= 0 && document.removeBodyElement(position);
            }
        }
        return false;
    }

    private static void removeParagraphExact(XWPFDocument document, String text) {
        for (XWPFParagraph paragraph : new ArrayList<>(document.getParagraphs())) {
            if (paragraph.getText().trim().equals(text)) {
                int position = document.getPosOfParagraph(paragraph);
                if (position >= 0) {
                    document.removeBodyElement(position);
                }
            }
        }
    }

    private static void removeTocEntry(XWPFDocument document, String heading) {
        for (XWPFParagraph paragraph : new ArrayList<>(document.getParagraphs())) {
            String value = paragraph.getText().trim();
            if (!value.equals(heading) && value.startsWith(heading)) {
                int position = document.getPosOfParagraph(paragraph);
                if (position >= 0) {
                    document.removeBodyElement(position);
                }
            }
        }
    }

    private static void removeTableAndCaption(XWPFDocument document, String caption) {
        List<IBodyElement> body = document.getBodyElements();
        for (int i = 0; i < body.size(); i++) {
            if (!(body.get(i) instanceof XWPFParagraph paragraph)
                    || !paragraph.getText().contains(caption)) {
                continue;
            }
            for (int j = i + 1; j < body.size(); j++) {
                if (body.get(j) instanceof XWPFTable) {
                    document.removeBodyElement(j);
                    break;
                }
                if (body.get(j) instanceof XWPFParagraph next && !next.getText().isBlank()) {
                    break;
                }
            }
            document.removeBodyElement(i);
            return;
        }
    }

    private static void removeResultSubsection(
            XWPFDocument document, String heading, String caption, String noteNeedle) {
        removeTableAndCaption(document, caption);
        removeParagraphExact(document, heading);
        if (noteNeedle != null) {
            removeParagraphContaining(document, noteNeedle);
        }
    }

    private static void removeBodyRange(XWPFDocument document, String startText, String endText) {
        List<IBodyElement> body = document.getBodyElements();
        int start = -1;
        int end = -1;
        for (int i = 0; i < body.size(); i++) {
            if (!(body.get(i) instanceof XWPFParagraph paragraph)) {
                continue;
            }
            String value = paragraph.getText().trim();
            if (start < 0 && value.equals(startText)) {
                start = i;
            } else if (start >= 0 && value.equals(endText)) {
                end = i;
                break;
            }
        }
        if (start < 0 || end < start) {
            return;
        }
        for (int i = end - 1; i >= start; i--) {
            document.removeBodyElement(i);
        }
    }

    private static void removeBodyRangeToEnd(XWPFDocument document, String startText) {
        List<IBodyElement> body = document.getBodyElements();
        int start = -1;
        for (int i = 0; i < body.size(); i++) {
            if (body.get(i) instanceof XWPFParagraph paragraph
                    && paragraph.getText().trim().equals(startText)) {
                start = i;
                break;
            }
        }
        if (start < 0) {
            return;
        }
        for (int i = body.size() - 1; i >= start; i--) {
            document.removeBodyElement(i);
        }
    }

    private static void removeBlankParagraphsBeforeHeading(XWPFDocument document, String heading) {
        List<IBodyElement> body = document.getBodyElements();
        int headingIndex = -1;
        for (int i = 0; i < body.size(); i++) {
            if (body.get(i) instanceof XWPFParagraph paragraph
                    && paragraph.getText().trim().equals(heading)) {
                headingIndex = i;
                break;
            }
        }
        for (int i = headingIndex - 1; i >= 0; i--) {
            if (!(document.getBodyElements().get(i) instanceof XWPFParagraph paragraph)
                    || !paragraph.getText().isBlank()) {
                break;
            }
            document.removeBodyElement(i);
        }
    }

    /**
     * The converted source template leaves several empty paragraphs plus a full-height section-break
     * paragraph between the landscape sampling table and the portrait narrative. WPS may push that
     * invisible paragraph onto its own page. Move the section properties to the preceding real
     * paragraph and discard only the empty boundary paragraphs.
     */
    private static void compactSectionBreakAfterHeading(XWPFDocument document, String heading) {
        List<IBodyElement> body = document.getBodyElements();
        int headingIndex = -1;
        for (int i = 0; i < body.size(); i++) {
            if (body.get(i) instanceof XWPFParagraph paragraph
                    && paragraph.getText().trim().equals(heading)) {
                headingIndex = i;
                break;
            }
        }
        if (headingIndex < 0 || !(body.get(headingIndex) instanceof XWPFParagraph headingParagraph)) {
            return;
        }
        int sectionIndex = -1;
        for (int i = headingIndex + 1; i < body.size(); i++) {
            if (!(body.get(i) instanceof XWPFParagraph paragraph) || !paragraph.getText().isBlank()) {
                break;
            }
            if (paragraph.getCTP().isSetPPr() && paragraph.getCTP().getPPr().isSetSectPr()) {
                sectionIndex = i;
                break;
            }
        }
        if (sectionIndex < 0) {
            return;
        }
        int sectionHeadingIndex = headingIndex;
        int previousIndex = headingIndex - 1;
        while (previousIndex >= 0
                && body.get(previousIndex) instanceof XWPFParagraph paragraph
                && paragraph.getText().isBlank()) {
            previousIndex--;
        }
        if (previousIndex >= 0 && body.get(previousIndex) instanceof XWPFParagraph previousHeading
                && previousHeading.getText().trim().equals("4 职业病危害因素识别")) {
            sectionHeadingIndex = previousIndex;
            previousIndex--;
        }
        while (previousIndex >= 0
                && body.get(previousIndex) instanceof XWPFParagraph paragraph
                && paragraph.getText().isBlank()) {
            previousIndex--;
        }
        if (previousIndex < 0 || !(body.get(previousIndex) instanceof XWPFParagraph precedingParagraph)) {
            return;
        }
        XWPFParagraph sectionParagraph = (XWPFParagraph) body.get(sectionIndex);
        if (!precedingParagraph.getCTP().isSetPPr()) {
            precedingParagraph.getCTP().addNewPPr();
        }
        precedingParagraph.getCTP().getPPr().setSectPr(sectionParagraph.getCTP().getPPr().getSectPr());
        for (int i = sectionIndex; i > headingIndex; i--) {
            document.removeBodyElement(i);
        }
        for (int i = headingIndex - 1; i > sectionHeadingIndex; i--) {
            if (document.getBodyElements().get(i) instanceof XWPFParagraph paragraph
                    && paragraph.getText().isBlank()) {
                document.removeBodyElement(i);
            }
        }
    }

    /**
     * The source template ends the table of contents with a next-page section break while the first
     * body heading also has page-break-before. When a shortened conditional TOC fits on one page,
     * the two breaks otherwise create a completely blank page. A continuous section boundary keeps
     * the template's body header/footer section but lets the heading's own page break start the body.
     */
    private static void useContinuousSectionBreakBeforeHeading(XWPFDocument document, String heading) {
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
            if (!paragraph.getCTP().isSetPPr() || !paragraph.getCTP().getPPr().isSetSectPr()) {
                continue;
            }
            var section = paragraph.getCTP().getPPr().getSectPr();
            var type = section.isSetType() ? section.getType() : section.addNewType();
            type.setVal(STSectionMark.CONTINUOUS);
            return;
        }
    }

    private static void compactSectionBreakBeforeHeading(XWPFDocument document, String heading) {
        List<IBodyElement> body = document.getBodyElements();
        int headingIndex = -1;
        int sectionIndex = -1;
        for (int i = 0; i < body.size(); i++) {
            if (body.get(i) instanceof XWPFParagraph paragraph
                    && paragraph.getText().trim().equals(heading)) {
                headingIndex = i;
                break;
            }
        }
        for (int i = headingIndex - 1; i >= 0; i--) {
            if (!(body.get(i) instanceof XWPFParagraph paragraph) || !paragraph.getText().isBlank()) {
                break;
            }
            if (paragraph.getCTP().isSetPPr() && paragraph.getCTP().getPPr().isSetSectPr()) {
                sectionIndex = i;
                break;
            }
        }
        if (sectionIndex < 0) {
            return;
        }

        int precedingIndex = sectionIndex - 1;
        while (precedingIndex >= 0
                && body.get(precedingIndex) instanceof XWPFParagraph paragraph
                && paragraph.getText().isBlank()) {
            precedingIndex--;
        }
        if (precedingIndex < 0 || !(body.get(precedingIndex) instanceof XWPFParagraph preceding)) {
            return;
        }

        XWPFParagraph sectionParagraph = (XWPFParagraph) body.get(sectionIndex);
        if (!preceding.getCTP().isSetPPr()) {
            preceding.getCTP().addNewPPr();
        }
        preceding.getCTP().getPPr().setSectPr(sectionParagraph.getCTP().getPPr().getSectPr());
        for (int i = headingIndex - 1; i > precedingIndex; i--) {
            document.removeBodyElement(i);
        }
    }

    private static void removeTrailingBlankParagraphs(XWPFDocument document) {
        for (int i = document.getBodyElements().size() - 1; i >= 0; i--) {
            IBodyElement element = document.getBodyElements().get(i);
            if (!(element instanceof XWPFParagraph paragraph) || !paragraph.getText().isBlank()) {
                break;
            }
            document.removeBodyElement(i);
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
        fillTable(table(document, tableIndex), headerRows, items, builder);
    }

    private static void fillTable(XWPFTable table, int headerRows, List<JsonNode> items,
                                  Function<JsonNode, List<String>> builder) {
        if (items == null || items.isEmpty()) {
            return;
        }
        if (table == null) {
            return;
        }
        for (int i = 0; i < Math.min(headerRows, table.getNumberOfRows()); i++) {
            XWPFTableRow headerRow = table.getRow(i);
            headerRow.setCantSplitRow(true);
            headerRow.setRepeatHeader(true);
            headerRow.getTableCells().forEach(cell -> cell.getParagraphs()
                    .forEach(paragraph -> paragraph.setKeepNext(true)));
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
        row.setCantSplitRow(true);
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

    private static void setCellFontSize(XWPFTable table, int row, int column, double fontSize) {
        if (table == null || row < 1 || column < 1 || table.getNumberOfRows() < row) {
            return;
        }
        XWPFTableRow tableRow = table.getRow(row - 1);
        if (tableRow.getTableCells().size() < column) {
            return;
        }
        for (XWPFParagraph paragraph : tableRow.getCell(column - 1).getParagraphs()) {
            for (XWPFRun run : paragraph.getRuns()) {
                run.setFontSize(fontSize);
            }
        }
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

    private static XWPFTable tableContaining(XWPFDocument document, String text) {
        return tableContainingAll(document, text);
    }

    private static XWPFTable tableContainingAll(XWPFDocument document, String... texts) {
        return document.getTables().stream()
                .filter(candidate -> {
                    String candidateText = candidate.getText();
                    for (String text : texts) {
                        if (!candidateText.contains(text)) {
                            return false;
                        }
                    }
                    return true;
                })
                .findFirst()
                .orElse(null);
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

    private static String resultContext(JsonNode node) {
        List<String> parts = new ArrayList<>();
        String location = text(node, "workLocation");
        String date = text(node, "measurementDate");
        String bodyPart = text(node, "measurementBodyPart");
        String band = text(node, "measurementBand");
        if (!location.isBlank()) parts.add(location);
        if (!date.isBlank()) parts.add(date);
        if (!bodyPart.isBlank()) parts.add("部位：" + bodyPart);
        if (!band.isBlank()) parts.add("波段：" + band);
        return String.join("\n", parts);
    }

    private static String effectiveLimit(JsonNode node) {
        String adjusted = decimalText(node, "adjustedLimitValue");
        String factor = decimalText(node, "reductionFactor");
        if (!adjusted.isBlank() && !factor.isBlank() && !"1".equals(factor)) {
            return adjusted + "（折减系数" + factor + "）";
        }
        return decimalText(node, "limitValue");
    }

    private static void embedQualificationCertificate(XWPFDocument document, JsonNode supplement) {
        String dataUrl = text(supplement, "qualificationCertificateImageDataUrl");
        if (dataUrl.isBlank()) return;
        for (XWPFParagraph paragraph : document.getParagraphs()) {
            if (!paragraph.getText().contains("资质证书")) continue;
            setParagraphText(paragraph, "");
            paragraph.setPageBreak(true);
            addDataUrlPicture(paragraph, dataUrl,
                    fallback(text(supplement, "qualificationCertificateFileName"), "qualification-certificate"),
                    430, 610);
            paragraph.setAlignment(ParagraphAlignment.CENTER);
            return;
        }
    }

    private static void embedReportPhotos(XWPFDocument document, JsonNode supplement) {
        JsonNode photos = supplement.path("reportPhotos");
        if (!photos.isArray() || photos.isEmpty()) return;
        List<XWPFParagraph> paragraphs = document.getParagraphs();
        // The first occurrence is the table-of-contents entry; the last one is the appendix body.
        for (int index = paragraphs.size() - 1; index >= 0; index--) {
            XWPFParagraph paragraph = paragraphs.get(index);
            if (!paragraph.getText().contains("附件5 合影")) continue;
            XWPFParagraph anchor = paragraph;
            for (JsonNode photo : photos) {
                XmlCursor cursor = anchor.getCTP().newCursor();
                cursor.toEndToken();
                cursor.toNextToken();
                XWPFParagraph photoParagraph = document.insertNewParagraph(cursor);
                cursor.dispose();
                photoParagraph.setAlignment(ParagraphAlignment.CENTER);
                photoParagraph.setSpacingBefore(120);
                photoParagraph.setSpacingAfter(120);
                addDataUrlPicture(photoParagraph, text(photo, "imageDataUrl"),
                        fallback(text(photo, "fileName"), "onsite-photo"), 420, 280);
                String caption = String.join(" ", List.of(text(photo, "takenDate"), text(photo, "caption")))
                        .trim();
                if (!caption.isBlank()) {
                    XWPFRun captionRun = photoParagraph.createRun();
                    captionRun.addBreak();
                    captionRun.setText(caption);
                    captionRun.setFontSize(9.0);
                }
                anchor = photoParagraph;
            }
            return;
        }
    }

    private static void addDataUrlPicture(
            XWPFParagraph paragraph, String dataUrl, String fileName, int widthPoints, int heightPoints) {
        if (dataUrl == null || dataUrl.isBlank()) return;
        int comma = dataUrl.indexOf(',');
        String metadata = comma >= 0 ? dataUrl.substring(0, comma).toLowerCase(Locale.ROOT) : "";
        String encoded = comma >= 0 ? dataUrl.substring(comma + 1) : dataUrl;
        int pictureType = metadata.contains("png") ? XWPFDocument.PICTURE_TYPE_PNG : XWPFDocument.PICTURE_TYPE_JPEG;
        try (ByteArrayInputStream input = new ByteArrayInputStream(Base64.getDecoder().decode(encoded))) {
            byte[] imageBytes = Base64.getDecoder().decode(encoded);
            var raster = javax.imageio.ImageIO.read(new ByteArrayInputStream(imageBytes));
            if (raster != null) {
                double scale = Math.min((double)widthPoints / raster.getWidth(), (double)heightPoints / raster.getHeight());
                widthPoints = (int)Math.round(raster.getWidth() * scale);
                heightPoints = (int)Math.round(raster.getHeight() * scale);
            }
            XWPFRun pictureRun = paragraph.createRun();
            pictureRun.addPicture(input, pictureType, fileName,
                    Units.toEMU(widthPoints), Units.toEMU(heightPoints));
        } catch (Exception ignored) {
            XWPFRun warning = paragraph.createRun();
            warning.setText("[图片无法读取：" + fileName + "]");
        }
    }

    private static String decimalText(JsonNode node, String field) {
        String value = text(node, field);
        if (value.isBlank()) {
            return "";
        }
        try {
            return new BigDecimal(value).stripTrailingZeros().toPlainString();
        } catch (Exception ignored) {
            return value;
        }
    }

    private static String resultType(JsonNode node) {
        return switch (normalizeMetric(text(node, "metricType"))) {
            case "TWA", "CTWA" -> "CTWA";
            case "STEL", "CSTE" -> "CSTE";
            case "CME", "MAC" -> "CME";
            case "PE", "CPE" -> "CPE";
            case "SILICA" -> "游离二氧化硅含量";
            case "LEX8H", "LEX,8H" -> "LEX,8h";
            case "LEX,W" -> "LEX,W";
            case "LAEQ" -> "LAeq";
            case "X_GAMMA", "RADIATION_DOSE_RATE" -> "周围剂量当量率";
            default -> text(node, "metricType");
        };
    }

    private static String limitType(JsonNode node) {
        return switch (normalizeMetric(text(node, "metricType"))) {
            case "TWA", "CTWA" -> "PC-TWA";
            case "STEL", "CSTE" -> "PC-STEL";
            case "CME", "MAC" -> "MAC";
            case "PE", "CPE" -> "PC-TWA";
            case "SILICA" -> "游离二氧化硅限值";
            case "LEX8H", "LEX,8H", "LEX,W", "LAEQ" -> "职业接触限值";
            case "X_GAMMA", "RADIATION_DOSE_RATE" -> "控制水平";
            default -> fallback(text(node, "metricType"), "限值");
        };
    }

    private static List<JsonNode> exposureRows(List<JsonNode> positions, List<JsonNode> results) {
        Map<String, List<String>> factorsByJob = new LinkedHashMap<>();
        Map<String, JsonNode> resultByJob = new LinkedHashMap<>();
        for (JsonNode result : results) {
            String key = jobKey(result);
            String factor = displayHazardFactor(result);
            factorsByJob.computeIfAbsent(key, ignored -> new ArrayList<>());
            if (!factorsByJob.get(key).contains(factor)) {
                factorsByJob.get(key).add(factor);
            }
            resultByJob.putIfAbsent(key, result);
        }
        List<JsonNode> rows = new ArrayList<>();
        for (Map.Entry<String, JsonNode> entry : resultByJob.entrySet()) {
            JsonNode result = entry.getValue();
            rows.add(object("workLocation", text(result, "workLocation"),
                    "jobName", text(result, "jobName"),
                    "factorName", String.join("、", factorsByJob.get(entry.getKey())),
                    "workerCount", String.valueOf(workerCountFor(result, positions))));
        }
        return rows;
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

    private static String multipleLimit(JsonNode node, int multiple) {
        try {
            BigDecimal limit = new BigDecimal(text(node, "limitValue"));
            if (limit.signum() > 0) {
                return limit.multiply(BigDecimal.valueOf(multiple)).stripTrailingZeros().toPlainString();
            }
        } catch (Exception ignored) {
        }
        return "/";
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
