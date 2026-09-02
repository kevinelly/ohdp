package com.dsf.ohdp.service;

import com.dsf.ohdp.dto.OhdpDto;
import java.io.File;
import java.util.List;
import org.springframework.data.domain.Page;
import java.time.LocalDate;

public interface OhdpProjectService {

    OhdpDto.ProjectResponse createProject(OhdpDto.ProjectRequest request);

    OhdpDto.ProjectResponse updateProject(Long id, OhdpDto.ProjectRequest request);

    OhdpDto.ProjectResponse findProjectById(Long id);

    Page<OhdpDto.ProjectResponse> findProjectPage(OhdpDto.ProjectQuery query);

    void deleteProject(Long id);

    OhdpDto.PositionResponse addPosition(Long projectId, OhdpDto.PositionRequest request);

    OhdpDto.PositionResponse updatePosition(Long positionId, OhdpDto.PositionRequest request);

    List<OhdpDto.PositionResponse> listPositions(Long projectId);

    void deletePosition(Long positionId);

    OhdpDto.ResultItemResponse addResult(Long projectId, OhdpDto.ResultItemRequest request);

    OhdpDto.ResultItemResponse updateResult(Long resultId, OhdpDto.ResultItemRequest request);

    OhdpDto.ResultBatchResponse addResultsBatch(Long projectId, OhdpDto.ResultBatchRequest request);

    List<OhdpDto.ResultItemResponse> listResults(Long projectId);

    void deleteResult(Long resultId);

    OhdpDto.JudgeResponse judge(Long projectId);

    OhdpDto.ReportFileResponse generateReport(Long projectId);

    OhdpDto.ReportPrecheckResponse precheckReport(Long projectId);

    OhdpDto.ReportPreviewResponse previewReport(Long projectId);

    List<OhdpDto.ReportFileResponse> listReports(Long projectId);

    List<OhdpDto.ReportOperationLogResponse> listReportOperationLogs(Long projectId);

    Page<OhdpDto.ReportCenterResponse> pageReportCenter(
            String projectCode, String companyName, String reportStatus,
            LocalDate generatedFrom, LocalDate generatedTo, int page, int size);

    OhdpDto.ProjectResponse copyProject(Long sourceProjectId);

    File getPreviewReportFile(Long projectId);

    File getReportFile(Long reportId);

    Page<OhdpDto.CompanyProfileResponse> pageCompanyProfiles(String keyword, int page, int size);

    OhdpDto.CompanyProfileResponse createCompanyProfile(OhdpDto.CompanyProfileRequest request);

    OhdpDto.CompanyProfileResponse updateCompanyProfile(Long id, OhdpDto.CompanyProfileRequest request);

    void deleteCompanyProfile(Long id);

    Page<OhdpDto.PositionTemplateResponse> pagePositionTemplates(String keyword, int page, int size);

    OhdpDto.PositionTemplateResponse createPositionTemplate(OhdpDto.PositionTemplateRequest request);

    OhdpDto.PositionTemplateResponse updatePositionTemplate(Long id, OhdpDto.PositionTemplateRequest request);

    void deletePositionTemplate(Long id);

    Page<OhdpDto.FactorTemplateResponse> pageFactorTemplates(String keyword, int page, int size);

    OhdpDto.FactorTemplateResponse createFactorTemplate(OhdpDto.FactorTemplateRequest request);

    OhdpDto.FactorTemplateResponse updateFactorTemplate(Long id, OhdpDto.FactorTemplateRequest request);

    void deleteFactorTemplate(Long id);

    List<OhdpDto.DictionaryItemResponse> listDictionaryItems(String dictType);

    OhdpDto.DictionaryItemResponse createDictionaryItem(OhdpDto.DictionaryItemRequest request);

    OhdpDto.DictionaryItemResponse updateDictionaryItem(Long id, OhdpDto.DictionaryItemRequest request);

    void deleteDictionaryItem(Long id);

    Page<OhdpDto.StaffProfileResponse> pageStaffProfiles(String keyword, int page, int size);

    OhdpDto.StaffProfileResponse createStaffProfile(OhdpDto.StaffProfileRequest request);

    OhdpDto.StaffProfileResponse updateStaffProfile(Long id, OhdpDto.StaffProfileRequest request);

    void deleteStaffProfile(Long id);

    Page<OhdpDto.ReportTextTemplateResponse> pageReportTextTemplates(String templateType, String keyword, int page, int size);

    List<OhdpDto.ReportTextTemplateResponse> listEnabledReportTextTemplates(String templateType);

    OhdpDto.ReportTextTemplateResponse createReportTextTemplate(OhdpDto.ReportTextTemplateRequest request);

    OhdpDto.ReportTextTemplateResponse updateReportTextTemplate(Long id, OhdpDto.ReportTextTemplateRequest request);

    void deleteReportTextTemplate(Long id);
}
