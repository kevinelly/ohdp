package com.dsf.ohdp;

import static org.assertj.core.api.Assertions.assertThat;

import com.dsf.ohdp.entity.OhdpProject;
import com.dsf.ohdp.entity.OhdpReportFile;
import com.dsf.ohdp.entity.ProjectStatus;
import com.dsf.ohdp.repository.OhdpProjectRepository;
import com.dsf.ohdp.repository.OhdpReportFileRepository;
import com.dsf.ohdp.service.OhdpProjectService;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class OhdpReportCenterIntegrationTest {

    @Autowired
    private OhdpProjectRepository projectRepository;

    @Autowired
    private OhdpReportFileRepository reportFileRepository;

    @Autowired
    private OhdpProjectService projectService;

    @Test
    void returnsGeneratedAndPendingProjectsWithChineseLabels() {
        String marker = "REPORT-CENTER-" + System.nanoTime();
        OhdpProject generated = project(marker + "-G", "北海市报告中心测试单位", ProjectStatus.REPORT_READY);
        OhdpProject pending = project(marker + "-P", "北海市待生成测试单位", ProjectStatus.JUDGED);
        generated = projectRepository.save(generated);
        pending = projectRepository.save(pending);

        OhdpReportFile report = new OhdpReportFile();
        report.setProjectId(generated.getId());
        report.setFileName(generated.getProjectCode() + "_report.doc");
        report.setFilePath("target/test-reports/" + report.getFileName());
        report.setTemplateVersion("TEST");
        report.setGeneratedBy("测试报告员");
        report.setGeneratedAt(LocalDateTime.now());
        reportFileRepository.save(report);

        var generatedPage = projectService.pageReportCenter(marker, "北海市", "GENERATED", null, null, 0, 20);
        assertThat(generatedPage.getContent()).hasSize(1);
        assertThat(generatedPage.getContent().get(0).getProjectId()).isEqualTo(generated.getId());
        assertThat(generatedPage.getContent().get(0).getProjectStatusLabel()).isEqualTo("已生成报告");
        assertThat(generatedPage.getContent().get(0).getReportStatusLabel()).isEqualTo("已生成");

        var pendingPage = projectService.pageReportCenter(marker, "北海市", "PENDING", null, null, 0, 20);
        assertThat(pendingPage.getContent()).hasSize(1);
        assertThat(pendingPage.getContent().get(0).getProjectId()).isEqualTo(pending.getId());
        assertThat(pendingPage.getContent().get(0).getProjectStatusLabel()).isEqualTo("已判定");
        assertThat(pendingPage.getContent().get(0).getReportStatusLabel()).isEqualTo("待生成");
    }

    private OhdpProject project(String code, String companyName, ProjectStatus status) {
        OhdpProject project = new OhdpProject();
        project.setProjectCode(code);
        project.setCompanyName(companyName);
        project.setCompanyAddress("北海市海城区测试路 1 号");
        project.setDetectType("职业病危害因素检测");
        project.setStatus(status);
        return project;
    }
}
