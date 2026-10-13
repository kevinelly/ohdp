package com.dsf.ohdp;

import com.dsf.ohdp.dto.OhdpDto;
import com.dsf.ohdp.service.OhdpProjectService;
import com.fasterxml.jackson.databind.*;
import java.nio.file.*;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties={"ohdp.report.storage-dir=./target/test-reports","ohdp.report.refresh-fields-on-windows=false"})
@ActiveProfiles("test")
class ReportWorkflowAcceptanceTest {
    @Autowired OhdpProjectService service;
    @Test void completeRecordedDataCanPassPrecheckAndGenerateAfterJudgement() throws Exception {
        ObjectMapper mapper=new ObjectMapper().findAndRegisterModules().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,false);
        var fixture=new ReportAuditRegressionTest().fixture();
        var staffRequest=new OhdpDto.StaffProfileRequest();staffRequest.setStaffName("测试报告员");staffRequest.setStaffTitle("主管技师");staffRequest.setProfession("职业卫生");staffRequest.setCertNo("QA-STAFF");
        var staff=service.createStaffProfile(staffRequest);
        assertThat(staff.getProfession()).isEqualTo("职业卫生");
        var request=new OhdpDto.ProjectRequest();request.setCompanyName("端到端验收测试（非真实企业）");request.setCompanyAddress("测试地址");request.setDetectType("定期检测");request.setSamplingDate(LocalDate.of(2026,9,10));request.setReportDate(LocalDate.of(2026,9,17));request.setStatementDate(request.getReportDate());request.setSampleName("测试样品");request.setTaskOrigin("验收测试委托");request.setEvaluationBasis("软件验收依据");request.setWriterStaffId(staff.getId());request.setReviewerStaffId(staff.getId());request.setApproverStaffId(staff.getId());
        var s=mapper.readValue(fixture.get("__REPORT_SUPPLEMENT_JSON__"),OhdpDto.ReportSupplement.class);
        s.setEquipmentConfirmedNotApplicable(true);s.setProtectionFacilitiesConfirmedNotApplicable(true);s.setRawRecordCompletenessNote("验收样例记录完整；非真实业务资料");s.setQualificationCertificatePlan("上传验收测试图片");s.setPhysicalFactorScope("按实际结果中的物理因素输出");s.setSpecialMeasurementRule("按已填部位及波段显示");s.setExclusionScreeningConclusion("本验收样例按已录入结果说明");s.setMajorHazards("样例因素详见结果");request.setReportSupplement(s);
        var project=service.createProject(request);
        var posRequest=new OhdpDto.PositionRequest();posRequest.setJobName("焊工");posRequest.setWorkLocation("焊接车间");posRequest.setWorkerCount(6);posRequest.setWorkingHours("10h/d，5d/w");posRequest.setShiftSystem("单班制");posRequest.setHazards("详见结果");
        var position=service.addPosition(project.getId(),posRequest);
        for(JsonNode n:mapper.readTree(fixture.get("__RESULTS_JSON__"))) {
            var result=mapper.treeToValue(n,OhdpDto.ResultItemRequest.class);result.setPositionId(position.getId());service.addResult(project.getId(),result);
        }
        assertThat(service.precheckReport(project.getId()).getMissingItems()).anyMatch(x->x.contains("未完成有效判定"));
        service.judge(project.getId());
        var check=service.precheckReport(project.getId());assertThat(check.getMissingItems()).isEmpty();assertThat(check.getPassed()).isTrue();
        var report=service.generateReport(project.getId(),true);assertThat(Files.size(Path.of(report.getFilePath()))).isGreaterThan(10000);
    }
}
