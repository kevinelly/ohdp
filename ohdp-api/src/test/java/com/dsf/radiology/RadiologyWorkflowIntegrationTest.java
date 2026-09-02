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
    void completesCaseCalculationApprovalAndDocxReport() throws Exception {
        RadiologyDto.CaseRequest request = new RadiologyDto.CaseRequest();
        request.setCaseCode("DR-IT-" + UUID.randomUUID().toString().substring(0, 8));
        request.setModality(RadiologyModality.DR);
        request.setInspectedUnitName("集成测试医院");
        request.setInspectedUnitAddress("测试路 1 号");
        request.setEquipmentName("数字化医用X射线摄影系统");
        request.setEquipmentModel("TEST-DR-01");
        request.setEquipmentNo("EQ-001");
        request.setInspectionDate(LocalDate.now());
        request.setWriterName("编写测试员");
        request.setInstrumentDescription("多功能X射线检测仪 / TEST-001 / 校准有效");
        var created = caseService.createCase(request);

        RadiologyDto.MeasurementBatchRequest values = new RadiologyDto.MeasurementBatchRequest();
        values.setValues(List.of(
                measurement("HVL", "HVL1", "2.20"),
                measurement("HVL", "HVL2", "2.30"),
                measurement("HVL", "HVL3", "2.40"),
                measurement("TVD", "TVD1-0", "60"),
                measurement("TVD", "TVD1-1", "60.2"),
                measurement("TVD", "TVD1-2", "59.9"),
                measurement("TVD", "TVD1-3", "60.1"),
                measurement("TVD", "TVD2-0", "80"),
                measurement("TVD", "TVD2-1", "80.2"),
                measurement("TVD", "TVD2-2", "79.9"),
                measurement("TVD", "TVD2-3", "80.1"),
                measurement("TVD", "TVD3-0", "100"),
                measurement("TVD", "TVD3-1", "100.2"),
                measurement("TVD", "TVD3-2", "99.9"),
                measurement("TVD", "TVD3-3", "100.1")));
        caseService.saveMeasurements(created.id(), values);
        var calculated = caseService.calculate(created.id());
        assertThat(calculated.completedItems()).isEqualTo(2);
        assertThat(calculated.items()).anyMatch(item -> item.itemCode().equals("HVL") && item.complete());
        assertThat(calculated.items()).anyMatch(item -> item.itemCode().equals("TVD") && item.complete());

        RadiologyDto.ProtectionPointRequest point = new RadiologyDto.ProtectionPointRequest();
        point.setPointCode("RPD1");
        point.setPointName("控制室操作位");
        point.setLocationDescription("距观察窗 30 cm");
        point.setReadings(List.of(new BigDecimal("88"), new BigDecimal("99"), new BigDecimal("98"), new BigDecimal("87"), new BigDecimal("86")));
        point.setSortNo(1);
        RadiologyDto.ProtectionPointBatchRequest points = new RadiologyDto.ProtectionPointBatchRequest();
        points.setPoints(List.of(point));
        caseService.saveProtectionPoints(created.id(), points);

        RadiologyDto.WorkflowRequest workflow = new RadiologyDto.WorkflowRequest();
        workflow.setOperatorName("审核测试员");
        workflow.setComment("数据与原始记录一致");
        assertThat(caseService.submitForReview(created.id(), workflow).status()).isEqualTo(RadiologyCaseStatus.PENDING_REVIEW);
        assertThat(caseService.review(created.id(), workflow).status()).isEqualTo(RadiologyCaseStatus.REVIEWED);
        workflow.setOperatorName("批准测试员");
        assertThat(caseService.approve(created.id(), workflow).status()).isEqualTo(RadiologyCaseStatus.APPROVED);

        var response = reportService.generate(created.id());
        var report = reportRepository.findByIdAndDeletedFalse(response.id()).orElseThrow();
        Path path = Path.of(report.getStoragePath());
        assertThat(path).exists().isRegularFile();
        assertThat(report.getSnapshotJson()).contains(created.caseCode(), "measurementCount", "attachmentHashes");
        try (XWPFDocument document = new XWPFDocument(Files.newInputStream(path))) {
            assertThat(document.getParagraphs()).extracting(paragraph -> paragraph.getText())
                    .anyMatch(text -> text.contains("放射诊疗设备质量控制检测报告"))
                    .anyMatch(text -> text.contains("综合评价"));
            assertThat(document.getTables()).hasSizeGreaterThanOrEqualTo(4);
        }
        assertThat(caseService.getCase(created.id()).status()).isEqualTo(RadiologyCaseStatus.REPORTED);
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

        var result = caseService.saveProtectionPoints(created.id(), points);
        assertThat(result).extracting(RadiologyDto.ProtectionPointResponse::resultValue)
                .containsExactly(new BigDecimal("30.000"), new BigDecimal("30.000"));
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
