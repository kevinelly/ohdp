package com.dsf.radiology;

import static org.assertj.core.api.Assertions.assertThat;

import com.dsf.radiology.dto.RadiologyDto;
import com.dsf.radiology.model.RadiologyCaseStatus;
import com.dsf.radiology.model.RadiologyJudgement;
import com.dsf.radiology.model.RadiologyModality;
import com.dsf.radiology.repository.RadiologyReportRepository;
import com.dsf.radiology.service.RadiologyCaseService;
import com.dsf.radiology.service.RadiologyReportService;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class RadiologyWorkflowIntegrationTest {

    @Autowired
    private RadiologyCaseService caseService;

    @Autowired
    private RadiologyReportService reportService;

    @Autowired
    private RadiologyReportRepository reportRepository;

    @Test
    void incompleteOldTwoItemFlowCannotApproveOrGenerateReport() {
        var created=caseService.createCase(RadiologyTestFixture.request());
        var batch=new RadiologyDto.MeasurementBatchRequest();
        batch.setValues(List.of(measurement("HUA","HUA1","0"),measurement("HUA","HUA2","0"),measurement("HUA","HUA3","0")));
        caseService.saveMeasurements(created.id(),batch);caseService.calculate(created.id());
        org.assertj.core.api.Assertions.assertThatThrownBy(()->caseService.submitForReview(created.id(),new RadiologyDto.WorkflowRequest()))
            .hasMessageContaining("无法提交");
    }

    @Test
    void continuesWithAnnualDoseAssessmentWhenDrDoseRateExceeds25() {
        RadiologyDto.CaseRequest request = new RadiologyDto.CaseRequest();
        request.setCaseCode("DR-ANNUAL-" + UUID.randomUUID().toString().substring(0, 8));
        request.setModality(RadiologyModality.DR);
        request.setInspectedUnitName("年剂量评估测试单位");
        request.setEquipmentName("数字化医用X射线摄影系统");
        var created = caseService.createCase(request);

        RadiologyDto.ProtectionPointRequest lowerAnnualDose = protectionPoint(
                "RPD-A", "高剂量率低年工作量点", "1000000", 1);
        RadiologyDto.ProtectionPointRequest higherAnnualDose = protectionPoint(
                "RPD-B", "高剂量率高年工作量点", "4000000", 2);
        RadiologyDto.ProtectionPointBatchRequest points = new RadiologyDto.ProtectionPointBatchRequest();
        points.setPoints(List.of(lowerAnnualDose, higherAnnualDose));

        caseService.saveProtectionPoints(created.id(), points);
        caseService.calculate(created.id());
        var result = caseService.getCase(created.id()).protectionPoints();
        assertThat(result).extracting(RadiologyDto.ProtectionPointResponse::resultValue)
                .allSatisfy(value -> assertThat(value).isEqualByComparingTo("30.000"));
        assertThat(result).extracting(RadiologyDto.ProtectionPointResponse::annualEffectiveDose)
                .containsExactly(new BigDecimal("0.08333333"), new BigDecimal("0.33333333"));
        assertThat(result).extracting(RadiologyDto.ProtectionPointResponse::judgement)
                .containsExactly(RadiologyJudgement.QUALIFIED, RadiologyJudgement.UNQUALIFIED);
    }

    private RadiologyDto.ProtectionPointRequest protectionPoint(
            String code, String name, String annualWorkload, int sortNo) {
        RadiologyDto.ProtectionPointRequest point = new RadiologyDto.ProtectionPointRequest();
        point.setPointCode(code);
        point.setPointName(name);
        point.setReadings(List.of(
                new BigDecimal("30000"), new BigDecimal("30000"), new BigDecimal("30000"),
                new BigDecimal("30000"), new BigDecimal("30000")));
        point.setAnnualWorkloadMas(new BigDecimal(annualWorkload));
        point.setOccupancyFactor(BigDecimal.ONE);
        point.setUseFactor(BigDecimal.ONE);
        point.setSortNo(sortNo);
        return point;
    }

    private RadiologyDto.MeasurementRequest measurement(String item, String field, String value) {
        RadiologyDto.MeasurementRequest request = new RadiologyDto.MeasurementRequest();
        request.setItemCode(item);
        request.setFieldCode(field);
        request.setValueNum(new BigDecimal(value));
        return request;
    }
}
