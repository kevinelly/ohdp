package com.dsf.radiology;

import static org.assertj.core.api.Assertions.*;
import com.dsf.radiology.dto.RadiologyDto;
import com.dsf.radiology.model.*;
import com.dsf.radiology.service.*;
import com.dsf.radiology.repository.RadiologyCaseRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.math.BigDecimal;
import java.util.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.mock.web.MockMultipartFile;

@SpringBootTest @ActiveProfiles("test")
class RadiologyCompletionOctober14Test {
    @Autowired RadiologyCaseService cases;
    @Autowired RadiologyReportService reports;
    @Autowired RadiologyBatchImportService batchImporter;
    @Autowired RadiologyCaseRepository repository;
    @Autowired RadiologyPdfPreviewService pdf;
    @Autowired ObjectMapper mapper;
    private RadiologyDto.MeasurementRequest v(String item,String field,String value){return RadiologyTestFixture.value(item,field,value);}
    private long create(RadiologyModality modality,String category){var r=RadiologyTestFixture.request();r.setModality(modality);r.setDetectionCategory(category);return cases.createCase(r).id();}
    private void save(long id,List<RadiologyDto.MeasurementRequest> values,Map<String,String> conditions){var r=new RadiologyDto.MeasurementBatchRequest();r.setValues(values);r.setConditions(conditions);r.setExpectedDataVersion(cases.getCase(id).dataVersion());cases.saveMeasurements(id,r);}
    private RadiologyDto.ItemCalculationResponse result(long id,String item){return cases.calculate(id).items().stream().filter(r->r.itemCode().equals(item)).findFirst().orElseThrow();}
    @Test void stateHcrUsesBaselineEvenWhenManufacturerAndNyquistArePresent() {
        long id=create(RadiologyModality.DR,"状态检测");
        save(id,List.of(v("HCR","HCR1","2"),v("HCR","HCR-MANUFACTURER","2"),v("HCR","HCR-NYQUIST","1"),v("HCR","HCR-BASELINE","3")),Map.of("HCR","80kV"));
        var r=result(id,"HCR");assertThat(r.resultValue()).isCloseTo(new BigDecimal("66.6666667"),within(new BigDecimal("0.000001")));assertThat(r.judgement()).isEqualTo(RadiologyJudgement.UNQUALIFIED);
        var cleared=new RadiologyDto.MeasurementRequest();cleared.setItemCode("HCR");cleared.setFieldCode("HCR-BASELINE");save(id,List.of(cleared),Map.of("HCR","80kV"));
        assertThat(result(id,"HCR").message()).contains("基线值");
    }
    @Test void acceptanceCtdiCanUseOnlyBodyWithoutRequiringHeadInputs() {
        long id=create(RadiologyModality.CT,"验收检测");var values=new ArrayList<RadiologyDto.MeasurementRequest>();
        values.addAll(List.of(v("CTDI","层数n2","1"),v("CTDI","层厚T2","10"),v("CTDI","刻度系数k2","1"),v("CTDI","CTDI-BODY-MANUFACTURER","10")));
        for(String d:List.of("C","U","D","L","R"))for(int i=1;i<=3;i++)values.add(v("CTDI","S"+d+"D"+i,"10"));
        save(id,values,Map.of("CTDI","体部模体120kV","__ctdi.parts","BODY"));
        assertThat(result(id,"CTDI").judgement()).isEqualTo(RadiologyJudgement.QUALIFIED);
        assertThat(cases.getCase(id).measurements()).noneMatch(m->m.fieldCode().equals("HEAD_CTDIW"));
        save(id,List.of(),Map.of("CTDI","120kV","__ctdi.parts",""));assertThat(result(id,"CTDI").complete()).isFalse();
        long state=create(RadiologyModality.CT,"状态检测");save(state,values,Map.of("__ctdi.parts","BODY"));assertThatThrownBy(()->cases.calculate(state)).hasMessageContaining("不检测体部模体");
    }
    @Test void voltageRowsKeepIndividualResultsAndDeletingFailedRowRemovesItFromReport() throws Exception {
        long id=create(RadiologyModality.DR,"验收检测");var values=new ArrayList<RadiologyDto.MeasurementRequest>();
        for(int row=1;row<=4;row++)for(int reading=0;reading<=3;reading++)values.add(v("TVD","R"+row+"__TVD1-"+reading,reading==0?"80":row==4?"90":"80"));
        save(id,values,Map.of("TVD","共同条件80kV","TVD.R4","失败测量条件","__rows.TVD","[1,2,3,4]"));
        assertThat(result(id,"TVD").judgement()).isEqualTo(RadiologyJudgement.UNQUALIFIED);
        var metadata=cases.getCase(id).measurements().stream().filter(m->m.fieldCode().equals("ROWS_JSON")).findFirst().orElseThrow();
        assertThat(mapper.readTree(metadata.valueText())).hasSize(4);
        try(var doc=new XWPFDocument(new ByteArrayInputStream(reports.preview(id)))) {assertThat(doc.getTables().stream().map(XWPFTable::getText).toList().toString()).contains("第4行","失败测量条件","不符合");}
        save(id,List.of(),Map.of("TVD","共同条件80kV","__rows.TVD","[1,2,3]"));assertThat(result(id,"TVD").judgement()).isEqualTo(RadiologyJudgement.QUALIFIED);
        assertThat(cases.getCase(id).measurements()).noneMatch(m->m.fieldCode().startsWith("R4__"));
        try(var doc=new XWPFDocument(new ByteArrayInputStream(reports.preview(id)))) {assertThat(doc.getTables().stream().map(XWPFTable::getText).toList().toString()).doesNotContain("第4行","失败测量条件");}
    }
    @Test void sliceThicknessExclusionsApplyPerRowRatherThanHidingEveryRow() throws Exception {
        long id=create(RadiologyModality.CT,"状态检测");var values=new ArrayList<RadiologyDto.MeasurementRequest>();
        for(int row=1;row<=2;row++) {String prefix="R"+row+"__";values.add(v("RST",prefix+"R0",row==1?"2":"3"));values.add(v("RST",prefix+"CTbackground","0"));values.add(v("RST",prefix+"CTmax","100"));for(int i=1;i<=4;i++)values.add(v("RST",prefix+"R"+i,"3"));values.add(v("RST",prefix+"RST-FACTOR","1"));}
        save(id,values,Map.of("RST","层厚条件","__rows.RST","[1,2]"));assertThat(result(id,"RST").judgement()).isEqualTo(RadiologyJudgement.QUALIFIED);
        var json=mapper.readTree(cases.getCase(id).measurements().stream().filter(m->m.fieldCode().equals("ROWS_JSON")).findFirst().orElseThrow().valueText());
        assertThat(json.get(0).get("judgement").asText()).isEqualTo("NOT_APPLICABLE");assertThat(json.get(1).get("judgement").asText()).isEqualTo("QUALIFIED");
    }
    @Test void incompleteAddedRowBlocksCalculationCompletionAndEmptyRowListIsRejected() {
        long id=create(RadiologyModality.DR,"状态检测");save(id,List.of(v("HCR","R1__HCR1","3"),v("HCR","R1__HCR-BASELINE","3")),Map.of("HCR","80kV","__rows.HCR","[1,2]"));
        assertThat(result(id,"HCR").complete()).isFalse();assertThat(cases.getCase(id).submissionIssues().toString()).contains("第2行");
        assertThatThrownBy(()->save(id,List.of(),Map.of("__rows.HCR","[]"))).hasMessageContaining("测量行");
    }
    private byte[] batch(String first,String second,boolean invalid) throws Exception {
        try(var workbook=new XSSFWorkbook(new ByteArrayInputStream(batchImporter.template()));var out=new ByteArrayOutputStream()) {
            var sheet=workbook.getSheet("批量案例");int row=1;
            for(String code:List.of(first,second)) {
                String[] data={code,"CT","受理"+code,"虚构批量测试单位","普通CT","状态检测","2026-10-14","2026-10-14","NPS","","120kV","NPS1",invalid && code.equals(second)?"错误文字":"3"};
                var r=sheet.createRow(row++);for(int i=0;i<data.length;i++)r.createCell(i).setCellValue(data[i]);
            }
            workbook.write(out);return out.toByteArray();
        }
    }
    private MockMultipartFile file(byte[] bytes){return new MockMultipartFile("file","batch.xlsx","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",bytes);}
    @Test void batchPreviewIsReadOnlyAndImportCreatesTwoCasesAndDuplicateRetryDoesNotOverwrite() throws Exception {
        String suffix=UUID.randomUUID().toString().substring(0,8),a="BATCH-A-"+suffix,b="BATCH-B-"+suffix;byte[] bytes=batch(a,b,false);
        assertThat(batchImporter.importWorkbook(file(bytes),true).cases()).isEqualTo(2);assertThat(repository.existsByCaseCode(a)).isFalse();
        assertThat(batchImporter.importWorkbook(file(bytes),false).appliedCases()).isEqualTo(2);assertThat(repository.existsByCaseCode(a)).isTrue();assertThat(repository.existsByCaseCode(b)).isTrue();
        assertThat(batchImporter.importWorkbook(file(bytes),false).appliedCases()).isZero();
    }
    @Test void batchFailureIsReportedByRowAndEntireBatchRemainsUnwritten() throws Exception {
        String suffix=UUID.randomUUID().toString().substring(0,8),a="BAD-A-"+suffix,b="BAD-B-"+suffix;
        var r=batchImporter.importWorkbook(file(batch(a,b,true)),false);assertThat(r.appliedCases()).isZero();assertThat(r.rows()).anyMatch(x->x.row()==3 && x.status().equals("ERROR"));assertThat(repository.existsByCaseCode(a)).isFalse();assertThat(repository.existsByCaseCode(b)).isFalse();
    }
    @Test void pdfPageImagesRenderWithoutBrowserPlugin() throws Exception {
        byte[] bytes;try(var doc=new org.apache.pdfbox.pdmodel.PDDocument();var out=new ByteArrayOutputStream()) {
            var page=new org.apache.pdfbox.pdmodel.PDPage();doc.addPage(page);
            try(var stream=new org.apache.pdfbox.pdmodel.PDPageContentStream(doc,page)) {stream.setNonStrokingColor(java.awt.Color.BLACK);stream.addRect(20,20,100,100);stream.fill();}
            doc.addPage(new org.apache.pdfbox.pdmodel.PDPage());doc.save(out);bytes=out.toByteArray();
        }
        var pages=pdf.renderPages(bytes);assertThat(pages).hasSize(2);assertThat(pages.get(0)).startsWith("data:image/png;base64,");assertThat(pages.get(0)).isNotEqualTo(pages.get(1));
    }
}
