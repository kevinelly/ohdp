package com.dsf.ohdp.controller;

import com.dsf.ohdp.dto.OhdpDto;
import com.dsf.ohdp.entity.ProjectStatus;
import com.dsf.ohdp.service.OhdpProjectService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "OHDP-Project")
@RestController
@RequestMapping("/api/ohdp")
@RequiredArgsConstructor
public class OhdpProjectController {

    private final OhdpProjectService projectService;

    @PostMapping("/projects")
    public OhdpDto.ProjectResponse createProject(@Valid @RequestBody OhdpDto.ProjectRequest request) {
        return projectService.createProject(request);
    }

    @PutMapping("/projects/{id}")
    public OhdpDto.ProjectResponse updateProject(@PathVariable Long id, @Valid @RequestBody OhdpDto.ProjectRequest request) {
        return projectService.updateProject(id, request);
    }

    @GetMapping("/projects/{id}")
    public OhdpDto.ProjectResponse getProject(@PathVariable Long id) {
        return projectService.findProjectById(id);
    }

    @GetMapping("/projects")
    public Page<OhdpDto.ProjectResponse> listProjects(
            @RequestParam(required = false) String projectCode,
            @RequestParam(required = false) String companyName,
            @RequestParam(required = false) ProjectStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        OhdpDto.ProjectQuery query = new OhdpDto.ProjectQuery();
        query.setProjectCode(projectCode);
        query.setCompanyName(companyName);
        query.setStatus(status);
        query.setPage(page);
        query.setSize(size);
        return projectService.findProjectPage(query);
    }

    @DeleteMapping("/projects/{id}")
    public void deleteProject(@PathVariable Long id) {
        projectService.deleteProject(id);
    }

    @PostMapping("/projects/{projectId}/copy")
    public OhdpDto.ProjectResponse copyProject(@PathVariable Long projectId) {
        return projectService.copyProject(projectId);
    }

    @PostMapping("/projects/{projectId}/positions")
    public OhdpDto.PositionResponse addPosition(
            @PathVariable Long projectId,
            @Valid @RequestBody OhdpDto.PositionRequest request) {
        return projectService.addPosition(projectId, request);
    }

    @PutMapping("/positions/{positionId}")
    public OhdpDto.PositionResponse updatePosition(
            @PathVariable Long positionId,
            @Valid @RequestBody OhdpDto.PositionRequest request) {
        return projectService.updatePosition(positionId, request);
    }

    @GetMapping("/projects/{projectId}/positions")
    public List<OhdpDto.PositionResponse> listPositions(@PathVariable Long projectId) {
        return projectService.listPositions(projectId);
    }

    @DeleteMapping("/positions/{positionId}")
    public void deletePosition(@PathVariable Long positionId) {
        projectService.deletePosition(positionId);
    }

    @PostMapping("/projects/{projectId}/results")
    public OhdpDto.ResultItemResponse addResult(
            @PathVariable Long projectId,
            @Valid @RequestBody OhdpDto.ResultItemRequest request) {
        return projectService.addResult(projectId, request);
    }

    @PostMapping("/projects/{projectId}/results/batch")
    public OhdpDto.ResultBatchResponse addResultBatch(
            @PathVariable Long projectId,
            @Valid @RequestBody OhdpDto.ResultBatchRequest request) {
        return projectService.addResultsBatch(projectId, request);
    }

    @PutMapping("/results/{resultId}")
    public OhdpDto.ResultItemResponse updateResult(
            @PathVariable Long resultId,
            @Valid @RequestBody OhdpDto.ResultItemRequest request) {
        return projectService.updateResult(resultId, request);
    }

    @GetMapping("/projects/{projectId}/results")
    public List<OhdpDto.ResultItemResponse> listResults(@PathVariable Long projectId) {
        return projectService.listResults(projectId);
    }

    @DeleteMapping("/results/{resultId}")
    public void deleteResult(@PathVariable Long resultId) {
        projectService.deleteResult(resultId);
    }

    @PostMapping("/projects/{projectId}/judge")
    public OhdpDto.JudgeResponse judge(@PathVariable Long projectId) {
        return projectService.judge(projectId);
    }

    @GetMapping("/projects/{projectId}/report/precheck")
    public OhdpDto.ReportPrecheckResponse precheckReport(@PathVariable Long projectId) {
        return projectService.precheckReport(projectId);
    }

    @GetMapping("/projects/{projectId}/report/preview")
    public OhdpDto.ReportPreviewResponse previewReport(@PathVariable Long projectId) {
        return projectService.previewReport(projectId);
    }

    @GetMapping("/projects/{projectId}/report/preview-file")
    public ResponseEntity<Resource> previewReportFile(@PathVariable Long projectId) throws FileNotFoundException {
        File file = projectService.getPreviewReportFile(projectId);
        String encoded = URLEncoder.encode(file.getName(), StandardCharsets.UTF_8).replace("+", "%20");
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "inline; filename*=UTF-8''" + encoded);
        InputStreamResource resource = new InputStreamResource(new FileInputStream(file));
        return ResponseEntity.ok()
                .headers(headers)
                .contentLength(file.length())
                .contentType(MediaType.parseMediaType("application/msword"))
                .body(resource);
    }

    @PostMapping("/projects/{projectId}/report/generate")
    public OhdpDto.ReportFileResponse generateReport(@PathVariable Long projectId) {
        return projectService.generateReport(projectId);
    }

    @GetMapping("/projects/{projectId}/reports")
    public List<OhdpDto.ReportFileResponse> listReports(@PathVariable Long projectId) {
        return projectService.listReports(projectId);
    }

    @GetMapping("/projects/{projectId}/report/logs")
    public List<OhdpDto.ReportOperationLogResponse> listReportOperationLogs(@PathVariable Long projectId) {
        return projectService.listReportOperationLogs(projectId);
    }

    @GetMapping("/reports/{reportId}/download")
    public ResponseEntity<Resource> downloadReport(@PathVariable Long reportId) throws FileNotFoundException {
        File file = projectService.getReportFile(reportId);
        String encoded = URLEncoder.encode(file.getName(), StandardCharsets.UTF_8).replace("+", "%20");
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded);
        InputStreamResource resource = new InputStreamResource(new FileInputStream(file));
        return ResponseEntity.ok()
                .headers(headers)
                .contentLength(file.length())
                .contentType(MediaType.parseMediaType("application/msword"))
                .body(resource);
    }

    @GetMapping("/maintenance/company-profiles")
    public Page<OhdpDto.CompanyProfileResponse> pageCompanyProfiles(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return projectService.pageCompanyProfiles(keyword, page, size);
    }

    @PostMapping("/maintenance/company-profiles")
    public OhdpDto.CompanyProfileResponse createCompanyProfile(@Valid @RequestBody OhdpDto.CompanyProfileRequest request) {
        return projectService.createCompanyProfile(request);
    }

    @PutMapping("/maintenance/company-profiles/{id}")
    public OhdpDto.CompanyProfileResponse updateCompanyProfile(
            @PathVariable Long id,
            @Valid @RequestBody OhdpDto.CompanyProfileRequest request) {
        return projectService.updateCompanyProfile(id, request);
    }

    @DeleteMapping("/maintenance/company-profiles/{id}")
    public void deleteCompanyProfile(@PathVariable Long id) {
        projectService.deleteCompanyProfile(id);
    }

    @GetMapping("/maintenance/position-templates")
    public Page<OhdpDto.PositionTemplateResponse> pagePositionTemplates(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return projectService.pagePositionTemplates(keyword, page, size);
    }

    @PostMapping("/maintenance/position-templates")
    public OhdpDto.PositionTemplateResponse createPositionTemplate(@Valid @RequestBody OhdpDto.PositionTemplateRequest request) {
        return projectService.createPositionTemplate(request);
    }

    @PutMapping("/maintenance/position-templates/{id}")
    public OhdpDto.PositionTemplateResponse updatePositionTemplate(
            @PathVariable Long id,
            @Valid @RequestBody OhdpDto.PositionTemplateRequest request) {
        return projectService.updatePositionTemplate(id, request);
    }

    @DeleteMapping("/maintenance/position-templates/{id}")
    public void deletePositionTemplate(@PathVariable Long id) {
        projectService.deletePositionTemplate(id);
    }

    @GetMapping("/maintenance/factor-templates")
    public Page<OhdpDto.FactorTemplateResponse> pageFactorTemplates(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return projectService.pageFactorTemplates(keyword, page, size);
    }

    @PostMapping("/maintenance/factor-templates")
    public OhdpDto.FactorTemplateResponse createFactorTemplate(@Valid @RequestBody OhdpDto.FactorTemplateRequest request) {
        return projectService.createFactorTemplate(request);
    }

    @PutMapping("/maintenance/factor-templates/{id}")
    public OhdpDto.FactorTemplateResponse updateFactorTemplate(
            @PathVariable Long id,
            @Valid @RequestBody OhdpDto.FactorTemplateRequest request) {
        return projectService.updateFactorTemplate(id, request);
    }

    @DeleteMapping("/maintenance/factor-templates/{id}")
    public void deleteFactorTemplate(@PathVariable Long id) {
        projectService.deleteFactorTemplate(id);
    }

    @GetMapping("/maintenance/dictionary-items")
    public List<OhdpDto.DictionaryItemResponse> listDictionaryItems(@RequestParam String dictType) {
        return projectService.listDictionaryItems(dictType);
    }

    @PostMapping("/maintenance/dictionary-items")
    public OhdpDto.DictionaryItemResponse createDictionaryItem(@Valid @RequestBody OhdpDto.DictionaryItemRequest request) {
        return projectService.createDictionaryItem(request);
    }

    @PutMapping("/maintenance/dictionary-items/{id}")
    public OhdpDto.DictionaryItemResponse updateDictionaryItem(
            @PathVariable Long id,
            @Valid @RequestBody OhdpDto.DictionaryItemRequest request) {
        return projectService.updateDictionaryItem(id, request);
    }

    @DeleteMapping("/maintenance/dictionary-items/{id}")
    public void deleteDictionaryItem(@PathVariable Long id) {
        projectService.deleteDictionaryItem(id);
    }
}
