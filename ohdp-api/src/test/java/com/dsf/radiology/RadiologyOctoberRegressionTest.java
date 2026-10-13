package com.dsf.radiology;
import static org.assertj.core.api.Assertions.*;
import com.dsf.radiology.service.*;
import com.dsf.radiology.dto.RadiologyDto;
import com.dsf.radiology.model.*;
import com.dsf.radiology.repository.*;
import com.dsf.auth.security.*;
import com.dsf.auth.entity.UserRole;
import java.math.BigDecimal;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.apache.poi.xwpf.usermodel.XWPFDocument;

@SpringBootTest @ActiveProfiles("test")
class RadiologyOctoberRegressionTest {
    @Autowired RadiologyCaseService cases;
    @Autowired RadiologyReportService reports;
    @Autowired RadiologyReportRepository reportRepository;
    @Autowired RadiologyCaseRepository caseRepository;
    @Autowired RadiologyAttachmentService attachments;
    @AfterEach void clearAuth() { AuthContext.clear(); }

    Long completeCase() {
        var id=cases.createCase(RadiologyTestFixture.request()).id();
        cases.saveMeasurements(id,RadiologyTestFixture.measurements());cases.saveProtectionPoints(id,RadiologyTestFixture.points());
        cases.calculate(id);return id;
    }
    @Test void drStateWithoutAecOrRawImagesCanProduceReportWithExplicitExclusions() throws Exception {
        var r=RadiologyTestFixture.request();r.setModality(RadiologyModality.DR);r.setEquipmentName("普通DR");
        r.setHasAec(false);r.setHasRawImages(false);r.setFeatureReason("虚构测试设备无AEC且不能导出原始图像");
        Long id=cases.createCase(r).id();cases.saveMeasurements(id,RadiologyTestFixture.drMeasurements());
        cases.saveProtectionPoints(id,RadiologyTestFixture.points());cases.calculate(id);
        assertThat(cases.getCase(id).submissionIssues()).isEmpty();
        var w=RadiologyTestFixture.workflow();cases.submitForReview(id,w);cases.review(id,w);cases.approve(id,w);
        var report=reports.generate(id);var record=reportRepository.findById(report.id()).orElseThrow();
        try(var doc=new XWPFDocument(Files.newInputStream(Path.of(record.getStoragePath())))) {
            assertThat(doc.getTables()).anyMatch(t->t.getText().contains("虚构测试设备无AEC且不能导出原始图像") && t.getText().contains("不适用"));
        }
    }
    @Test void revisionsCopyAttachmentsWithoutChangingApprovedOriginalAndHaveUniqueNumbers() throws Exception {
        Long id=completeCase();
        var file=new org.springframework.mock.web.MockMultipartFile("file","proof.pdf","application/pdf","software test attachment".getBytes());
        var original=attachments.upload(id,"CERTIFICATE",file);cases.calculate(id);
        var w=RadiologyTestFixture.workflow();cases.submitForReview(id,w);cases.review(id,w);cases.approve(id,w);
        var r2=cases.reopenAsRevision(id);var r3=cases.reopenAsRevision(id);
        assertThat(r2.revisionNo()).isEqualTo(2);assertThat(r3.revisionNo()).isEqualTo(3);
        assertThat(r2.attachments()).hasSize(1);attachments.delete(r2.attachments().get(0).id());
        assertThat(attachments.download(original.id()).resource().getInputStream().readAllBytes()).isEqualTo(file.getBytes());
        assertThat(cases.getCase(id).status()).isEqualTo(RadiologyCaseStatus.APPROVED);
    }
    @Test void headOnlyStateCompletesAllRequiredItemsAndProducesFrozenFiveColumnReport() throws Exception {
        Long id=completeCase();var detail=cases.getCase(id);
        assertThat(detail.submissionIssues()).isEmpty();
        assertThat(detail.itemResults()).hasSize(12).anyMatch(r->r.itemCode().equals("GTA") && r.judgement()==RadiologyJudgement.NOT_APPLICABLE);
        var w=RadiologyTestFixture.workflow();cases.submitForReview(id,w);cases.review(id,w);cases.approve(id,w);
        var result=reports.generate(id);
        assertThat(result.reportNo()).isEqualTo(detail.sampleAcceptanceNo());
        var record=reportRepository.findById(result.id()).orElseThrow();
        assertThat(record.getSnapshotJson()).contains("templateSha256","HEAD_CTDIW","不检测此项");
        try(var doc=new XWPFDocument(Files.newInputStream(Path.of(record.getStoragePath())))) {
            assertThat(doc.getTables()).anyMatch(t->t.getRow(0).getTableCells().size()==5 && t.getText().contains("检测条件"));
            assertThat(doc.getTables()).anyMatch(t->t.getText().contains("扫描架倾角精度") && t.getText().contains("不适用"));
            String text=doc.getParagraphs().stream().map(p->p.getText()).collect(java.util.stream.Collectors.joining());
            assertThat(text).contains("放射性职业病危害因素检测报告","综合评价");
        }
        // Changing master rules after approval must not alter archived conclusions.
        var rule=new RadiologyDto.RuleRequest();rule.setModality(RadiologyModality.CT);rule.setItemCode("NPS");
        rule.setRuleVersion("October-"+UUID.randomUUID());rule.setDetectionCategory("状态检测");rule.setUpperLimit(new BigDecimal("0.01"));
        rule.setUnit("%");rule.setStandardCode("TEST");rule.setStandardName("软件验收规则");cases.saveRule(null,rule);
        var second=reports.generate(id);
        assertThat(second.reportNo()).isEqualTo(result.reportNo());
        assertThat(reportRepository.findById(second.id()).orElseThrow().getSnapshotJson()).isEqualTo(record.getSnapshotJson());
    }
    @Test void missingUntouchedItemBlocksSubmission() {
        Long id=cases.createCase(RadiologyTestFixture.request()).id();
        var batch=RadiologyTestFixture.measurements();
        batch.setValues(batch.getValues().stream().filter(v->!v.getItemCode().equals("NPS")).toList());
        cases.saveMeasurements(id,batch);cases.saveProtectionPoints(id,RadiologyTestFixture.points());cases.calculate(id);
        assertThat(cases.getCase(id).submissionIssues()).anyMatch(i->i.contains("噪声"));
        assertThatThrownBy(()->cases.submitForReview(id,RadiologyTestFixture.workflow())).hasMessageContaining("噪声");
    }
    @Test void editingRawDataInvalidatesOldResultsAndStaleClientsAreRejected() {
        Long id=completeCase();long version=cases.getCase(id).dataVersion();
        var batch=new RadiologyDto.MeasurementBatchRequest();
        batch.setValues(List.of(RadiologyTestFixture.value("NPS","NPS1","4")));batch.setExpectedDataVersion(version);
        var edited=cases.saveMeasurements(id,batch);
        assertThat(edited.calculationFresh()).isFalse();assertThat(edited.itemResults()).isEmpty();
        assertThat(edited.measurements()).noneMatch(v->v.fieldRole()!=RadiologyFieldRole.RAW);
        assertThatThrownBy(()->cases.saveMeasurements(id,batch)).hasMessageContaining("数据已被");
        assertThatThrownBy(()->cases.submitForReview(id,RadiologyTestFixture.workflow())).isInstanceOf(RuntimeException.class);
    }
    @Test void returnRequiresReasonPreservesHistoryAndAllowsCorrection() {
        Long id=completeCase();var w=RadiologyTestFixture.workflow();cases.submitForReview(id,w);
        w.setComment("");assertThatThrownBy(()->cases.returnForCorrection(id,w)).hasMessageContaining("退回意见");
        w.setComment("请复核本底条件");var returned=cases.returnForCorrection(id,w);
        assertThat(returned.status()).isEqualTo(RadiologyCaseStatus.DATA_ENTERED);
        assertThat(returned.workflowHistory()).extracting(RadiologyDto.WorkflowEvent::action).containsExactly("SUBMIT","RETURN");
        assertThat(returned.workflowHistory().get(1).operator()).isEqualTo("system");
    }
    @Test void ordinaryUserCannotReviewAndClientOperatorCannotImpersonateReviewer() {
        Long id=completeCase();var w=RadiologyTestFixture.workflow();cases.submitForReview(id,w);
        AuthContext.set(AuthenticatedUser.builder().userId(999999L).username("ordinary").role(UserRole.USER).build());
        assertThatThrownBy(()->cases.review(id,w)).hasMessageContaining("无权限");
        AuthContext.set(AuthenticatedUser.builder().userId(1L).username("real-admin").role(UserRole.ADMIN).build());
        assertThat(cases.review(id,w).reviewerName()).isEqualTo("real-admin");
    }
    @Test void protectionUnitConversionAndResponseCorrectionAreExplicit() {
        var request=RadiologyTestFixture.request();request.setRpdUnit("μSv/h");request.setProtectionMode("RESPONSE_CORRECTED");
        Long id=cases.createCase(request).id();var batch=RadiologyTestFixture.measurements();
        var values=new ArrayList<>(batch.getValues());values.removeIf(v->v.getItemCode().equals("RPD"));
        for(int i=1;i<=10;i++) values.add(RadiologyTestFixture.value("RPD","RPD0-"+i,"0.1"));
        values.add(RadiologyTestFixture.value("RPD","RPD-k2","2"));batch.setValues(values);cases.saveMeasurements(id,batch);
        var points=RadiologyTestFixture.points();points.getPoints().get(0).setReadings(Collections.nCopies(5,new BigDecimal("1.2")));
        cases.saveProtectionPoints(id,points);cases.calculate(id);
        assertThat(cases.getCase(id).protectionPoints().get(0).resultValue()).isEqualByComparingTo("2.2");
    }
    @Test void customRuleIsActuallyExecutedAndRuleChangeExpiresCalculations() {
        Long id=completeCase();var r=new RadiologyDto.RuleRequest();r.setModality(RadiologyModality.CT);r.setItemCode("MTF");
        r.setRuleVersion("CUSTOM-"+UUID.randomUUID());r.setDetectionCategory("状态检测");r.setUnit("lp/cm");
        r.setLowerLimit(new BigDecimal("6"));r.setLowerInclusive(false);r.setStandardCode("TEST");r.setStandardName("软件验收");
        cases.saveRule(null,r);assertThat(cases.getCase(id).calculationFresh()).isFalse();
        assertThat(cases.calculate(id).items()).anyMatch(i->i.itemCode().equals("MTF") && i.judgement()==RadiologyJudgement.UNQUALIFIED);
    }
}

