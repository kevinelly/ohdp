package com.dsf.radiology;

import static org.assertj.core.api.Assertions.*;
import com.dsf.radiology.dto.RadiologyDto;
import com.dsf.radiology.model.*;
import com.dsf.radiology.service.*;
import com.dsf.radiology.repository.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest @ActiveProfiles("test")
class RadiologyGapWorkflowTest {
    @Autowired RadiologyCaseService cases;
    @Autowired RadiologyReportService reports;
    @Autowired RadiologyReportRepository reportRepository;
    @Autowired RadiologyCaseRepository caseRepository;
    @Autowired RadiologyMeasurementRepository measurementRepository;
    @Autowired RadiologyAttachmentService attachments;
    @Test void completeDrAcceptanceWithFirstBaselinesAndWebpMapCanBeApproved() throws Exception {
        var r=request();r.setModality(RadiologyModality.DR);r.setDetectionCategory("验收检测");r.setHasAec(true);r.setHasRawImages(true);
        r.getProfile().setSelectedGroups(List.of("GENERAL","SPECIAL","PROTECTION"));
        Long id=cases.createCase(r).id();var batch=RadiologyTestFixture.drMeasurements();var v=batch.getValues();
        v.removeIf(x->(x.getItemCode().equals("DDI")&&x.getFieldCode().equals("DDI-BASELINE")) || (x.getItemCode().equals("HCR")&&x.getFieldCode().equals("厂家规定值/基线值")));
        for(int i=1;i<=5;i++) {v.add(RadiologyTestFixture.value("ARP","ARP"+i,"10.00"));v.add(RadiologyTestFixture.value("RUE","RUE"+i,"7"));v.add(RadiologyTestFixture.value("STP","STP-K"+i,""+i));v.add(RadiologyTestFixture.value("STP","STP-R"+i,""+(2*i+3)));}
        for(int g=1;g<=2;g++) {v.add(RadiologyTestFixture.value("ROQ","I"+g,"100"));v.add(RadiologyTestFixture.value("ROQ","T"+g,"0.1"));for(int i=1;i<=5;i++){v.add(RadiologyTestFixture.value("ROQ","ROQ"+g+"-"+i,"10"));v.add(RadiologyTestFixture.value("ARS","ARS"+g+"-"+i,"10.00"));}}
        for(int g=1;g<=3;g++) for(int i=0;i<=3;i++) v.add(RadiologyTestFixture.value("ETD","ETD"+g+"-"+i,"100.00"));
        for(String direction:List.of("C","L","R")) for(int i=1;i<=3;i++) v.add(RadiologyTestFixture.value("AIC","AIC-"+direction+i,"10"));
        for(String[] t:new String[][]{{"ILA","是否有残影（如“否”无需填后两项）","否"},{"STP","STP-MODEL","LINEAR"}}) {var m=new RadiologyDto.MeasurementRequest();m.setItemCode(t[0]);m.setFieldCode(t[1]);m.setValueText(t[2]);v.add(m);}
        for(String code:List.of("ROQ","ETD","ARP","ARS","AIC","STP","RUE","ILA")) batch.getConditions().put(code,"80 kV，SID=1 m；虚构验收条件");
        byte[] image=getClass().getResourceAsStream("/radiology/point-map.webp").readAllBytes();
        var first=attachments.upload(id,"POINT_MAP",new org.springframework.mock.web.MockMultipartFile("file","first.webp","image/webp",image));
        var replacement=attachments.replace(first.id(),new org.springframework.mock.web.MockMultipartFile("file","replacement.webp","image/webp",image));
        assertThat(attachments.list(id)).extracting(RadiologyDto.AttachmentResponse::id).containsExactly(replacement.id());
        assertThatThrownBy(()->attachments.replace(replacement.id(),new org.springframework.mock.web.MockMultipartFile("file","bad.webp","image/webp",new byte[]{1,2,3}))).hasMessageContaining("点位图");
        assertThat(attachments.list(id)).hasSize(1);
        cases.saveMeasurements(id,batch);cases.saveProtectionPoints(id,RadiologyTestFixture.points());cases.calculate(id);
        var detail=cases.getCase(id);assertThat(detail.submissionIssues()).isEmpty();
        assertThat(detail.itemResults()).filteredOn(x->List.of("DDI","HCR","LCR").contains(x.itemCode())).allMatch(x->x.judgement()==RadiologyJudgement.BASELINE_ESTABLISHED);
        assertThat(detail.measurements()).filteredOn(m->m.itemCode().equals("ARP")&&m.fieldCode().equals("MEAN")).extracting(RadiologyDto.MeasurementResponse::displayValue).containsExactly("10.00");
        assertThat(detail.itemResults()).filteredOn(x->x.itemCode().equals("STP")).extracting(RadiologyDto.ItemCalculationResponse::resultText).containsExactly("1.0000");
        var w=RadiologyTestFixture.workflow();cases.submitForReview(id,w);cases.review(id,w);cases.approve(id,w);var report=reports.generate(id);
        Path path=Path.of(reportRepository.findById(report.id()).orElseThrow().getStoragePath());Files.createDirectories(Path.of("../.task/round2-qa"));Files.copy(path,Path.of("../.task/round2-qa/DR-full.docx"),StandardCopyOption.REPLACE_EXISTING);
        try(var doc=new XWPFDocument(Files.newInputStream(path))) {assertThat(doc.getAllPictures()).isNotEmpty();assertThat(doc.getTables().stream().map(t->t.getText()).collect(java.util.stream.Collectors.joining())).contains("建立基线","1.0000");}
    }
    RadiologyDto.CaseRequest request() {
        var r=RadiologyTestFixture.request();r.setInspectionEndDate(LocalDate.of(2026,10,14));r.setCity("测试地市");r.setCounty("测试县区");
        var p=new RadiologyProfile();p.setSampleName("软件验收CT样品");p.setCommissionedUnit("软件验收委托单位");p.setSubmittingUnit("软件验收送检单位");p.setBackgroundLocation("虚构本底点位");p.setProtectionConditions("120kV，100mA，1s，水模体");
        p.setSelectedGroups(List.of("QC","PROTECTION"));r.setProfile(p);return r;
    }
    Long complete(RadiologyDto.CaseRequest r) {
        Long id=cases.createCase(r).id();cases.saveMeasurements(id,RadiologyTestFixture.measurements());cases.saveProtectionPoints(id,RadiologyTestFixture.points());cases.calculate(id);return id;
    }
    @Test void draftPreviewDoesNotIssueOrMutateCaseAndRetainsOriginalFrontMatter() throws Exception {
        Long id=complete(request());var before=cases.getCase(id);byte[] bytes=reports.preview(id);
        assertThat(cases.getCase(id).dataVersion()).isEqualTo(before.dataVersion());assertThat(reports.list(id)).isEmpty();
        try(var doc=new XWPFDocument(new java.io.ByteArrayInputStream(bytes))) {
            String text=doc.getParagraphs().stream().map(p->p.getText()).collect(java.util.stream.Collectors.joining());
            assertThat(text).contains("预览稿","十五日","本检验检测报告书总页数");
            assertThat(doc.getTables().stream().map(t->t.getText()).collect(java.util.stream.Collectors.joining())).contains("软件验收委托单位","软件验收送检单位","前进","后退","体部模体");
        }
    }
    @Test void officialReportUsesApprovedProfileAndTraceableDeletionKeepsFile() throws Exception {
        Long id=complete(request());var w=RadiologyTestFixture.workflow();cases.submitForReview(id,w);cases.review(id,w);cases.approve(id,w);
        var first=reports.generate(id);assertThat(first.fileName()).startsWith("检测报告--").contains("[CT-软件验收虚构医院]");
        Path path=Path.of(reportRepository.findById(first.id()).orElseThrow().getStoragePath());
        Path qa=Path.of("../.task/round2-qa");Files.createDirectories(qa);Files.copy(path,qa.resolve("CT.docx"),StandardCopyOption.REPLACE_EXISTING);
        reports.deleteReport(first.id(),"虚构软件验收报告清理");assertThat(reports.list(id)).isEmpty();assertThat(path).exists();
        assertThat(reportRepository.findById(first.id()).orElseThrow().getDeletionReason()).contains("软件验收");
        assertThatThrownBy(()->reports.deleteReport(first.id()," ")).hasMessageContaining("原因");
    }
    @Test void queryCombinesOverlappingDatesYearCityCountyReceiptAndModalities() {
        var r=request();Long id=cases.createCase(r).id();
        assertThat(cases.searchCases(null,List.of(RadiologyModality.CT),null,null,2026,LocalDate.of(2026,10,14),LocalDate.of(2026,10,14),"测试地市","测试县区",r.getSampleAcceptanceNo(),"虚构医院",0,20).getContent()).extracting(RadiologyDto.CaseSummary::id).contains(id);
        assertThat(cases.searchCases(null,List.of(RadiologyModality.DR),null,null,2026,null,null,"测试地市","测试县区",r.getSampleAcceptanceNo(),null,0,20).getContent()).isEmpty();
        assertThat(cases.searchCases(null,null,null,null,2025,null,null,null,null,r.getSampleAcceptanceNo(),null,0,20).getContent()).isEmpty();
    }
    @Test void selectedProtectionOnlyGroupDoesNotRequireQualityReadings() {
        var r=request();r.getProfile().setSelectedGroups(List.of("PROTECTION"));Long id=cases.createCase(r).id();
        var raw=new RadiologyDto.MeasurementBatchRequest();var values=new ArrayList<RadiologyDto.MeasurementRequest>();for(int i=1;i<=10;i++)values.add(RadiologyTestFixture.value("RPD","RPD0-"+i,"100"));raw.setValues(values);
        cases.saveMeasurements(id,raw);cases.saveProtectionPoints(id,RadiologyTestFixture.points());cases.calculate(id);
        assertThat(cases.getCase(id).submissionIssues()).isEmpty();
        assertThat(cases.getCase(id).itemResults()).filteredOn(v->!v.itemCode().equals("RPD")).allMatch(v->v.judgement()==RadiologyJudgement.NOT_APPLICABLE);
    }
    @Test void timeOnlyCorrectionWorksWithoutSubtractingBackgroundOrApplyingInstrumentFactor() {
        var r=request();r.getProfile().setCalibrationMode("TIME");Long id=cases.createCase(r).id();
        var raw=RadiologyTestFixture.measurements();raw.getValues().add(RadiologyTestFixture.value("RPD","k","5"));raw.getValues().add(RadiologyTestFixture.value("RPD","RPD-k2","2"));
        cases.saveMeasurements(id,raw);cases.saveProtectionPoints(id,RadiologyTestFixture.points());cases.calculate(id);
        assertThat(cases.getCase(id).protectionPoints().get(0).resultValue()).isEqualByComparingTo("2.4");
    }
    @Test void drReportContainsSeparateGroupsDirectionsAndExplicitImageExclusions() throws Exception {
        var r=request();r.setModality(RadiologyModality.DR);r.setHasAec(false);r.setHasRawImages(false);r.setFeatureReason("测试设备无法获取预处理图像");r.getProfile().setSelectedGroups(List.of("GENERAL","SPECIAL","PROTECTION"));
        Long id=cases.createCase(r).id();cases.saveMeasurements(id,RadiologyTestFixture.drMeasurements());cases.saveProtectionPoints(id,RadiologyTestFixture.points());cases.calculate(id);
        var w=RadiologyTestFixture.workflow();cases.submitForReview(id,w);cases.review(id,w);cases.approve(id,w);var report=reports.generate(id);
        Path path=Path.of(reportRepository.findById(report.id()).orElseThrow().getStoragePath());Files.createDirectories(Path.of("../.task/round2-qa"));Files.copy(path,Path.of("../.task/round2-qa/DR.docx"),StandardCopyOption.REPLACE_EXISTING);
        try(var doc=new XWPFDocument(Files.newInputStream(path))) {
            String tables=doc.getTables().stream().map(t->t.getText()).collect(java.util.stream.Collectors.joining());
            assertThat(tables).contains("第1组","左a1","右a2","水平","垂直","测试设备无法获取预处理图像");
            assertThat(doc.getParagraphs()).extracting(p->p.getText()).anyMatch(v->v.contains("数字X射线摄影（DR）设备专用检测项目"));
        }
    }
}
