package com.dsf.ohdp.service;

import com.dsf.auth.security.AuthContext;
import com.dsf.auth.security.AuthenticatedUser;
import com.dsf.common.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.dsf.ohdp.config.OhdpReportProperties;
import com.dsf.ohdp.dto.OhdpDto;
import com.dsf.ohdp.entity.OhdpCompanyProfile;
import com.dsf.ohdp.entity.OhdpDictionaryItem;
import com.dsf.ohdp.entity.OhdpFactorTemplate;
import com.dsf.ohdp.entity.OhdpPositionTemplate;
import com.dsf.ohdp.entity.OhdpProject;
import com.dsf.ohdp.entity.OhdpProjectPosition;
import com.dsf.ohdp.entity.OhdpProjectPositionRel;
import com.dsf.ohdp.entity.OhdpReportFile;
import com.dsf.ohdp.entity.OhdpReportOperationLog;
import com.dsf.ohdp.entity.OhdpReportTextTemplate;
import com.dsf.ohdp.entity.OhdpResultItem;
import com.dsf.ohdp.entity.OhdpStaffProfile;
import com.dsf.ohdp.entity.ProjectStatus;
import com.dsf.ohdp.repository.OhdpCompanyProfileRepository;
import com.dsf.ohdp.repository.OhdpDictionaryItemRepository;
import com.dsf.ohdp.repository.OhdpFactorTemplateRepository;
import com.dsf.ohdp.repository.OhdpPositionTemplateRepository;
import com.dsf.ohdp.repository.OhdpProjectPositionRepository;
import com.dsf.ohdp.repository.OhdpProjectPositionRelRepository;
import com.dsf.ohdp.repository.OhdpProjectRepository;
import com.dsf.ohdp.repository.OhdpReportFileRepository;
import com.dsf.ohdp.repository.OhdpReportOperationLogRepository;
import com.dsf.ohdp.repository.OhdpReportTextTemplateRepository;
import com.dsf.ohdp.repository.OhdpResultItemRepository;
import com.dsf.ohdp.repository.OhdpStaffProfileRepository;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.usermodel.Range;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OhdpProjectServiceImpl implements OhdpProjectService {

    private static final DateTimeFormatter CODE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final DateTimeFormatter CHINESE_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy\u5e74M\u6708d\u65e5");
    private static final Pattern NUMBER_PATTERN = Pattern.compile("-?\\d+(\\.\\d+)?");
    private static final Pattern MOJIBAKE_PATTERN = Pattern.compile("\uFFFD");
    private static final String REPORT_TEMPLATE_PATH = "templates/occupational_report_template.doc";
    private static final Random RANDOM = new Random();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String WORD_REPLACE_SCRIPT = """
            param(
                [string]$templatePath,
                [string]$outputPath,
                [string]$jsonPath
            )
            $ErrorActionPreference = 'Stop'
            $word = $null
            $doc = $null
            try {
                $word = New-Object -ComObject Word.Application
                $word.Visible = $false
                $word.DisplayAlerts = 0
                $doc = $word.Documents.Open($templatePath)
                $obj = Get-Content -Path $jsonPath -Raw -Encoding UTF8 | ConvertFrom-Json
                foreach ($prop in $obj.PSObject.Properties) {
                    $findText = [string]$prop.Name
                    if ($findText.StartsWith('__')) { continue }
                    if ([string]::IsNullOrWhiteSpace($findText)) { continue }
                    $replaceText = [string]$prop.Value
                    $doc.Content.Find.Execute($findText, $false, $false, $false, $false, $false, $true, 1, $false, $replaceText, 2) | Out-Null
                }
                if ($obj.PSObject.Properties.Name -contains '__APPEND_SUMMARY__') {
                    $doc.Content.InsertAfter(\"`r`n`r`n\" + [string]$obj.__APPEND_SUMMARY__) | Out-Null
                }
                $doc.SaveAs([ref]$outputPath)
                $doc.Close()
                $word.Quit()
                exit 0
            } catch {
                if ($doc -ne $null) { try { $doc.Close() } catch {} }
                if ($word -ne $null) { try { $word.Quit() } catch {} }
                Write-Output $_.Exception.Message
                exit 1
            }
            """;

    private static final String JUDGE_PENDING = "\u672a\u5224\u5b9a";
    private static final String JUDGE_PASS = "\u5408\u683c";
    private static final String JUDGE_FAIL = "\u4e0d\u5408\u683c";
    private static final String DEFAULT_DETECT_TYPE = "\u76d1\u6d4b\u68c0\u9a8c";
    private static final String DEFAULT_SAMPLE_NAME = "\u5de5\u4f5c\u573a\u6240\u7a7a\u6c14\u3001\u566a\u58f0\u7b49";
    private static final String DEFAULT_SUBMIT_UNIT = "/";
    private static final String DEFAULT_ENTRUST_UNIT = "/";
    private static final String DEFAULT_WRITER_NAME = "/";
    private static final String DEFAULT_REVIEWER_NAME = "/";
    private static final String DEFAULT_APPROVER_NAME = "/";
    private static final String DEFAULT_WRITER_CERT_NO = "\u6842\u804c\u9632\u57f9\uff08\u804c\u536b\u8bc4\u4ef7\uff092023-XXX\u53f7";
    private static final String DEFAULT_REVIEWER_CERT_NO = "\u6842\u804c\u9632\u57f9\uff08\u804c\u536b\u8bc4\u4ef7\uff092023-XXXX\u53f7";
    private static final String DEFAULT_APPROVER_CERT_NO = "\u6842\u804c\u9632\u57f9\uff08\u804c\u536b\u8bc4\u4ef7\uff092023-XXXX\u53f7";
    private static final String DEFAULT_EVALUATION_BASIS =
            "1.\u300a\u5de5\u4f5c\u573a\u6240\u6709\u5bb3\u56e0\u7d20\u804c\u4e1a\u63a5\u89e6\u9650\u503c \u7b2c1\u90e8\u5206\uff1a\u5316\u5b66\u6709\u5bb3\u56e0\u7d20\u300b\uff08GBZ 2.1-2019\uff09\uff08\u542b\u7b2c1\u3001\u7b2c2\u53f7\u4fee\u6539\u5355\uff09\n"
                    + "2.\u300a\u5de5\u4f5c\u573a\u6240\u6709\u5bb3\u56e0\u7d20\u804c\u4e1a\u63a5\u89e6\u9650\u503c \u7b2c2\u90e8\u5206\uff1a\u7269\u7406\u56e0\u7d20\u300b\uff08GBZ 2.2-2007\uff09";

    private static final String TEMPLATE_PROJECT_CODE = "\u804c202500XX";
    private static final String TEMPLATE_COMPANY_A = "XXXXXXXXXXXXXX\u516c\u53f8";
    private static final String TEMPLATE_COMPANY_B = "XXXXXXXXXXXX\u516c\u53f8";
    private static final String TEMPLATE_COMPANY_C = "XXXXXXXXXXX\u516c\u53f8";
    private static final String TEMPLATE_COMPANY_D = "XXXXXX\u6709\u9650\u516c\u53f8";
    private static final String TEMPLATE_COMPANY_ADDRESS = "\u5317\u6d77\u5e02XXXXXXXXXXXXXXXXXX";
    private static final String TEMPLATE_DETECT_TYPE = "\u76d1\u6d4b\u68c0\u9a8c";
    private static final String TEMPLATE_SAMPLING_DATE = "2025\u5e748\u670821\u65e5";
    private static final String TEMPLATE_REPORT_DATE = "2025\u5e7412\u67084\u65e5";
    private static final String TEMPLATE_SAMPLE_NAME = "\u5de5\u4f5c\u573a\u6240\u7a7a\u6c14\u7b49";
    private static final String TEMPLATE_SAMPLE_NAME_ALT = "\u5de5\u4f5c\u573a\u6240\u7a7a\u6c14\u3001\u566a\u58f0\u7b49";
    private static final String TEMPLATE_PROJECT_CODE_ALT = "OHDP-20260410-001";
    private static final String TEMPLATE_COMPANY_ALT = "\u5e7f\u897f\u793a\u4f8b\u79d1\u6280\u6709\u9650\u516c\u53f8";
    private static final String TEMPLATE_COMPANY_C_ALT = "XXX\u5e7f\u897f\u793a\u4f8b\u79d1\u6280\u6709\u9650\u516c\u53f8";
    private static final String TEMPLATE_COMPANY_ADDRESS_ALT = "\u5e7f\u897f\u5317\u6d77\u5e02\u6d77\u57ce\u533a\u793a\u4f8b\u5927\u905388\u53f7";
    private static final String TEMPLATE_STATEMENT_COMPANY = "XXXXXXXXXXX\u516c\u53f8";

    private static final String REPORT_TEMPLATE_VERSION = "WORD-TEMPLATE-V2";
    private static final String REPORT_FILE_SUFFIX = "_report.doc";
    private static final String PREVIEW_FILE_PREFIX = "preview_";
    private static final long PREVIEW_RETENTION_MILLIS = 6L * 60 * 60 * 1000;
    private static final String TEMPLATE_TYPE_TASK_ORIGIN = "TASK_ORIGIN";
    private static final String TEMPLATE_TYPE_EVALUATION_BASIS = "EVALUATION_BASIS";

    private final OhdpProjectRepository projectRepository;
    private final OhdpProjectPositionRepository positionRepository;
    private final OhdpProjectPositionRelRepository projectPositionRelRepository;
    private final OhdpResultItemRepository resultItemRepository;
    private final OhdpReportFileRepository reportFileRepository;
    private final OhdpReportOperationLogRepository reportOperationLogRepository;
    private final OhdpCompanyProfileRepository companyProfileRepository;
    private final OhdpPositionTemplateRepository positionTemplateRepository;
    private final OhdpFactorTemplateRepository factorTemplateRepository;
    private final OhdpDictionaryItemRepository dictionaryItemRepository;
    private final OhdpStaffProfileRepository staffProfileRepository;
    private final OhdpReportTextTemplateRepository reportTextTemplateRepository;
    private final OhdpReportProperties reportProperties;

    @Override
    @Transactional
    public OhdpDto.ProjectResponse createProject(OhdpDto.ProjectRequest request) {
        OhdpProject project = new OhdpProject();
        project.setProjectCode(generateProjectCode());
        applyProjectRequest(project, request, true);
        project.setStatus(ProjectStatus.DRAFT);
        project.setDeleted(false);
        return toProjectResponse(projectRepository.save(project));
    }

    @Override
    @Transactional
    public OhdpDto.ProjectResponse updateProject(Long id, OhdpDto.ProjectRequest request) {
        OhdpProject project = getActiveProject(id);
        applyProjectRequest(project, request, false);
        return toProjectResponse(projectRepository.save(project));
    }

    @Override
    @Transactional(readOnly = true)
    public OhdpDto.ProjectResponse findProjectById(Long id) {
        return toProjectResponse(getActiveProject(id));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OhdpDto.ProjectResponse> findProjectPage(OhdpDto.ProjectQuery query) {
        Pageable pageable = PageRequest.of(
                Math.max(query.getPage(), 0),
                Math.max(query.getSize(), 1),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));

        String projectCode = normalize(query.getProjectCode());
        String companyName = normalize(query.getCompanyName());
        if (query.getStatus() == null) {
            return projectRepository
                    .findByDeletedFalseAndProjectCodeContainingAndCompanyNameContaining(projectCode, companyName, pageable)
                    .map(this::toProjectResponse);
        }
        return projectRepository
                .findByDeletedFalseAndProjectCodeContainingAndCompanyNameContainingAndStatus(
                        projectCode, companyName, query.getStatus(), pageable)
                .map(this::toProjectResponse);
    }

    @Override
    @Transactional
    public void deleteProject(Long id) {
        OhdpProject project = getActiveProject(id);
        project.setDeleted(true);
        projectRepository.save(project);
    }

    @Override
    @Transactional
    public OhdpDto.PositionResponse addPosition(Long projectId, OhdpDto.PositionRequest request) {
        getActiveProject(projectId);
        OhdpProjectPosition position = new OhdpProjectPosition();
        position.setProjectId(null);
        position.setDeleted(false);
        applyPositionRequest(position, request, true);
        OhdpProjectPosition saved = positionRepository.save(position);
        bindPositionToProject(projectId, saved.getId());
        markProjectDataFilled(projectId);
        return toPositionResponse(saved, projectId);
    }

    @Override
    @Transactional
    public OhdpDto.PositionResponse updatePosition(Long positionId, OhdpDto.PositionRequest request) {
        OhdpProjectPosition position = positionRepository
                .findByIdAndDeletedFalse(positionId)
                .orElseThrow(() -> new BusinessException("position not found: " + positionId));
        applyPositionRequest(position, request, false);
        OhdpProjectPosition saved = positionRepository.save(position);
        List<OhdpProjectPositionRel> relList = projectPositionRelRepository.findByPositionIdAndDeletedFalseOrderByIdAsc(positionId);
        for (OhdpProjectPositionRel rel : relList) {
            markProjectDataFilled(rel.getProjectId());
        }
        Long responseProjectId = relList.isEmpty() ? null : relList.get(0).getProjectId();
        return toPositionResponse(saved, responseProjectId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OhdpDto.PositionResponse> listPositions(Long projectId) {
        getActiveProject(projectId);
        return listProjectPositions(projectId)
                .stream()
                .map(position -> toPositionResponse(position, projectId))
                .toList();
    }

    @Override
    @Transactional
    public void deletePosition(Long positionId) {
        OhdpProjectPosition position = positionRepository
                .findByIdAndDeletedFalse(positionId)
                .orElseThrow(() -> new BusinessException("position not found: " + positionId));
        position.setDeleted(true);
        positionRepository.save(position);
        List<OhdpProjectPositionRel> relList = projectPositionRelRepository.findByPositionIdAndDeletedFalseOrderByIdAsc(positionId);
        for (OhdpProjectPositionRel rel : relList) {
            rel.setDeleted(true);
        }
        if (!relList.isEmpty()) {
            projectPositionRelRepository.saveAll(relList);
        }
    }

    @Override
    @Transactional
    public OhdpDto.ResultItemResponse addResult(Long projectId, OhdpDto.ResultItemRequest request) {
        getActiveProject(projectId);
        OhdpResultItem item = new OhdpResultItem();
        item.setProjectId(projectId);
        item.setDeleted(false);
        applyResultRequest(item, request, true);
        item.setJudgement(JUDGE_PENDING);
        OhdpResultItem saved = resultItemRepository.save(item);
        markProjectDataFilled(projectId);
        return toResultResponse(saved);
    }

    @Override
    @Transactional
    public OhdpDto.ResultItemResponse updateResult(Long resultId, OhdpDto.ResultItemRequest request) {
        OhdpResultItem item = resultItemRepository
                .findByIdAndDeletedFalse(resultId)
                .orElseThrow(() -> new BusinessException("result item not found: " + resultId));
        applyResultRequest(item, request, false);
        item.setJudgement(JUDGE_PENDING);
        OhdpResultItem saved = resultItemRepository.save(item);
        markProjectDataFilled(item.getProjectId());
        return toResultResponse(saved);
    }

    @Override
    @Transactional
    public OhdpDto.ResultBatchResponse addResultsBatch(Long projectId, OhdpDto.ResultBatchRequest request) {
        getActiveProject(projectId);
        if (request == null || request.getItems() == null || request.getItems().isEmpty()) {
            throw new BusinessException("batch items cannot be empty");
        }
        List<OhdpResultItem> toSave = new ArrayList<>();
        for (OhdpDto.ResultItemRequest itemRequest : request.getItems()) {
            OhdpResultItem item = new OhdpResultItem();
            item.setProjectId(projectId);
            item.setDeleted(false);
            applyResultRequest(item, itemRequest, true);
            item.setJudgement(JUDGE_PENDING);
            toSave.add(item);
        }
        resultItemRepository.saveAll(toSave);
        markProjectDataFilled(projectId);
        return OhdpDto.ResultBatchResponse.builder()
                .projectId(projectId)
                .addedCount(toSave.size())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<OhdpDto.ResultItemResponse> listResults(Long projectId) {
        getActiveProject(projectId);
        return resultItemRepository.findByProjectIdAndDeletedFalseOrderByIdAsc(projectId)
                .stream()
                .map(this::toResultResponse)
                .toList();
    }

    @Override
    @Transactional
    public void deleteResult(Long resultId) {
        OhdpResultItem item = resultItemRepository
                .findByIdAndDeletedFalse(resultId)
                .orElseThrow(() -> new BusinessException("result item not found: " + resultId));
        item.setDeleted(true);
        resultItemRepository.save(item);
    }

    @Override
    @Transactional
    public OhdpDto.JudgeResponse judge(Long projectId) {
        OhdpProject project = getActiveProject(projectId);
        List<OhdpResultItem> items = resultItemRepository.findByProjectIdAndDeletedFalseOrderByIdAsc(projectId);
        int qualifiedCount = 0;
        int unqualifiedCount = 0;
        int judgedCount = 0;
        for (OhdpResultItem item : items) {
            String judgement = evaluate(item);
            item.setJudgement(judgement);
            if (!JUDGE_PENDING.equals(judgement)) {
                judgedCount++;
            }
            if (JUDGE_PASS.equals(judgement)) {
                qualifiedCount++;
            }
            if (JUDGE_FAIL.equals(judgement)) {
                unqualifiedCount++;
            }
        }
        resultItemRepository.saveAll(items);
        if (!items.isEmpty()) {
            project.setStatus(ProjectStatus.JUDGED);
            projectRepository.save(project);
        }
        return OhdpDto.JudgeResponse.builder()
                .projectId(projectId)
                .totalCount(items.size())
                .judgedCount(judgedCount)
                .qualifiedCount(qualifiedCount)
                .unqualifiedCount(unqualifiedCount)
                .build();
    }

    @Override
    @Transactional
    public OhdpDto.ReportFileResponse generateReport(Long projectId) {
        OhdpProject project = getActiveProject(projectId);
        fillDefaultReportFields(project);
        project = projectRepository.save(project);
        OhdpDto.ReportPrecheckResponse precheck = buildReportPrecheck(projectId, project);
        if (!Boolean.TRUE.equals(precheck.getPassed())) {
            throw new BusinessException("report precheck failed: " + String.join("; ", precheck.getMissingItems()));
        }

        Path dir = resolveReportStorageDir();

        String fileName = project.getProjectCode() + REPORT_FILE_SUFFIX;
        Path filePath = dir.resolve(fileName);

        List<OhdpProjectPosition> positions = listProjectPositions(projectId);
        List<OhdpResultItem> results = resultItemRepository.findByProjectIdAndDeletedFalseOrderByIdAsc(projectId);
        writeReportDocument(filePath, project, positions, results);

        OhdpReportFile reportFile = reportFileRepository.findByProjectIdAndDeletedFalse(projectId).orElse(null);
        boolean overwrite = reportFile != null;
        if (reportFile == null) {
            reportFile = new OhdpReportFile();
            reportFile.setProjectId(projectId);
            reportFile.setDeleted(false);
        }
        reportFile.setFileName(fileName);
        reportFile.setFilePath(filePath.toString());
        reportFile.setTemplateVersion(REPORT_TEMPLATE_VERSION);
        reportFile.setGeneratedBy(currentUsername());
        reportFile.setGeneratedAt(LocalDateTime.now());
        OhdpReportFile saved = reportFileRepository.save(reportFile);

        project.setStatus(ProjectStatus.REPORT_READY);
        projectRepository.save(project);

        if (overwrite) {
            logReportOperation(projectId, "OVERWRITE", "report regenerated and old file overwritten");
        } else {
            logReportOperation(projectId, "CREATE", "report generated");
        }
        return toReportResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public OhdpDto.ReportPrecheckResponse precheckReport(Long projectId) {
        OhdpProject project = getActiveProject(projectId);
        return buildReportPrecheck(projectId, project);
    }

    @Override
    @Transactional(readOnly = true)
    public OhdpDto.ReportPreviewResponse previewReport(Long projectId) {
        OhdpProject project = getActiveProject(projectId);
        List<OhdpDto.PositionResponse> positions = listProjectPositions(projectId)
                .stream()
                .map(position -> toPositionResponse(position, projectId))
                .toList();
        List<OhdpDto.ResultItemResponse> results = resultItemRepository.findByProjectIdAndDeletedFalseOrderByIdAsc(projectId)
                .stream()
                .map(this::toResultResponse)
                .toList();
        return OhdpDto.ReportPreviewResponse.builder()
                .project(toProjectResponse(project))
                .positions(positions)
                .results(results)
                .precheck(buildReportPrecheck(projectId, project))
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<OhdpDto.ReportFileResponse> listReports(Long projectId) {
        getActiveProject(projectId);
        return reportFileRepository.findByProjectIdAndDeletedFalseOrderByGeneratedAtDesc(projectId)
                .stream()
                .map(this::toReportResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<OhdpDto.ReportOperationLogResponse> listReportOperationLogs(Long projectId) {
        getActiveProject(projectId);
        return reportOperationLogRepository.findByProjectIdAndDeletedFalseOrderByOperatedAtDesc(projectId)
                .stream()
                .map(this::toReportOperationLogResponse)
                .toList();
    }

    @Override
    @Transactional
    public OhdpDto.ProjectResponse copyProject(Long sourceProjectId) {
        OhdpProject source = getActiveProject(sourceProjectId);
        OhdpProject copied = new OhdpProject();
        copied.setProjectCode(generateProjectCode());
        copied.setCompanyProfileId(source.getCompanyProfileId());
        copied.setCompanyName(source.getCompanyName());
        copied.setCompanyAddress(source.getCompanyAddress());
        copied.setDetectTypeDictItemId(source.getDetectTypeDictItemId());
        copied.setDetectType(source.getDetectType());
        copied.setSamplingDate(source.getSamplingDate());
        copied.setReportDate(source.getReportDate());
        copied.setSampleName(source.getSampleName());
        copied.setSubmitUnitCompanyId(source.getSubmitUnitCompanyId());
        copied.setSubmitUnit(source.getSubmitUnit());
        copied.setEntrustUnitCompanyId(source.getEntrustUnitCompanyId());
        copied.setEntrustUnit(source.getEntrustUnit());
        copied.setTaskOriginTemplateId(source.getTaskOriginTemplateId());
        copied.setTaskOrigin(source.getTaskOrigin());
        copied.setEvaluationBasisTemplateId(source.getEvaluationBasisTemplateId());
        copied.setEvaluationBasis(source.getEvaluationBasis());
        copied.setWriterStaffId(source.getWriterStaffId());
        copied.setWriterName(source.getWriterName());
        copied.setReviewerStaffId(source.getReviewerStaffId());
        copied.setReviewerName(source.getReviewerName());
        copied.setApproverStaffId(source.getApproverStaffId());
        copied.setApproverName(source.getApproverName());
        copied.setWriterCertNo(source.getWriterCertNo());
        copied.setReviewerCertNo(source.getReviewerCertNo());
        copied.setApproverCertNo(source.getApproverCertNo());
        copied.setStatementDate(source.getStatementDate());
        copied.setNotes(source.getNotes());
        copied.setStatus(ProjectStatus.DRAFT);
        copied.setDeleted(false);
        copied = projectRepository.save(copied);

        List<OhdpProjectPosition> sourcePositions = listProjectPositions(sourceProjectId);
        if (!sourcePositions.isEmpty()) {
            for (OhdpProjectPosition src : sourcePositions) {
                bindPositionToProject(copied.getId(), src.getId());
            }
        }

        List<OhdpResultItem> sourceResults = resultItemRepository.findByProjectIdAndDeletedFalseOrderByIdAsc(sourceProjectId);
        if (!sourceResults.isEmpty()) {
            List<OhdpResultItem> copiedResults = new ArrayList<>();
            for (OhdpResultItem src : sourceResults) {
                OhdpResultItem target = new OhdpResultItem();
                target.setProjectId(copied.getId());
                target.setPositionId(src.getPositionId());
                target.setJobName(src.getJobName());
                target.setFactorName(src.getFactorName());
                target.setMetricType(src.getMetricType());
                target.setValueRaw(src.getValueRaw());
                target.setValueNum(src.getValueNum());
                target.setUnit(src.getUnit());
                target.setLimitValue(src.getLimitValue());
                target.setLimitUnit(src.getLimitUnit());
                target.setJudgement(JUDGE_PENDING);
                target.setDeleted(false);
                copiedResults.add(target);
            }
            resultItemRepository.saveAll(copiedResults);
        }

        if (!sourcePositions.isEmpty() || !sourceResults.isEmpty()) {
            copied.setStatus(ProjectStatus.DATA_FILLED);
            copied = projectRepository.save(copied);
        }
        return toProjectResponse(copied);
    }

    @Override
    public File getPreviewReportFile(Long projectId) {
        OhdpProject project = getActiveProject(projectId);
        Path previewDir = resolveReportStorageDir().resolve("preview");
        try {
            Files.createDirectories(previewDir);
        } catch (IOException e) {
            throw new BusinessException("failed to prepare preview directory");
        }
        cleanupPreviewFiles(previewDir);

        String fileName = PREVIEW_FILE_PREFIX
                + project.getProjectCode()
                + "_"
                + LocalDateTime.now().format(CODE_TIME_FORMAT)
                + ".doc";
        Path filePath = previewDir.resolve(fileName);
        List<OhdpProjectPosition> positions = listProjectPositions(projectId);
        List<OhdpResultItem> results = resultItemRepository.findByProjectIdAndDeletedFalseOrderByIdAsc(projectId);
        writeReportDocument(filePath, project, positions, results);
        return filePath.toFile();
    }

    @Override
    @Transactional(readOnly = true)
    public File getReportFile(Long reportId) {
        OhdpReportFile reportFile = reportFileRepository
                .findByIdAndDeletedFalse(reportId)
                .orElseThrow(() -> new BusinessException("report file not found: " + reportId));
        File file = new File(reportFile.getFilePath());
        if (!file.exists() || !file.isFile()) {
            throw new BusinessException("report file is missing on disk");
        }
        return file;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OhdpDto.CompanyProfileResponse> pageCompanyProfiles(String keyword, int page, int size) {
        return companyProfileRepository
                .findByDeletedFalseAndCompanyNameContaining(normalize(keyword), pageRequest(page, size))
                .map(this::toCompanyProfileResponse);
    }

    @Override
    @Transactional
    public OhdpDto.CompanyProfileResponse createCompanyProfile(OhdpDto.CompanyProfileRequest request) {
        String companyName = requireText(request.getCompanyName(), "companyName");
        if (companyProfileRepository.existsByCompanyNameAndDeletedFalse(companyName)) {
            throw new BusinessException("company already exists");
        }
        OhdpCompanyProfile entity = new OhdpCompanyProfile();
        entity.setCompanyName(companyName);
        entity.setCompanyAddress(requireText(request.getCompanyAddress(), "companyAddress"));
        entity.setContactName(trimNullable(request.getContactName()));
        entity.setContactPhone(trimNullable(request.getContactPhone()));
        entity.setIndustryType(trimNullable(request.getIndustryType()));
        entity.setNotes(trimNullable(request.getNotes()));
        entity.setDeleted(false);
        return toCompanyProfileResponse(companyProfileRepository.save(entity));
    }

    @Override
    @Transactional
    public OhdpDto.CompanyProfileResponse updateCompanyProfile(Long id, OhdpDto.CompanyProfileRequest request) {
        OhdpCompanyProfile entity = companyProfileRepository
                .findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BusinessException("company profile not found: " + id));
        String companyName = requireText(request.getCompanyName(), "companyName");
        if (companyProfileRepository.existsByCompanyNameAndDeletedFalseAndIdNot(companyName, id)) {
            throw new BusinessException("company already exists");
        }
        entity.setCompanyName(companyName);
        entity.setCompanyAddress(requireText(request.getCompanyAddress(), "companyAddress"));
        entity.setContactName(trimNullable(request.getContactName()));
        entity.setContactPhone(trimNullable(request.getContactPhone()));
        entity.setIndustryType(trimNullable(request.getIndustryType()));
        entity.setNotes(trimNullable(request.getNotes()));
        return toCompanyProfileResponse(companyProfileRepository.save(entity));
    }

    @Override
    @Transactional
    public void deleteCompanyProfile(Long id) {
        OhdpCompanyProfile entity = companyProfileRepository
                .findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BusinessException("company profile not found: " + id));
        entity.setDeleted(true);
        companyProfileRepository.save(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OhdpDto.PositionTemplateResponse> pagePositionTemplates(String keyword, int page, int size) {
        return positionTemplateRepository
                .findByDeletedFalseAndJobNameContaining(normalize(keyword), pageRequest(page, size))
                .map(this::toPositionTemplateResponse);
    }

    @Override
    @Transactional
    public OhdpDto.PositionTemplateResponse createPositionTemplate(OhdpDto.PositionTemplateRequest request) {
        OhdpPositionTemplate entity = new OhdpPositionTemplate();
        entity.setJobName(requireText(request.getJobName(), "jobName"));
        entity.setShiftSystem(trimNullable(request.getShiftSystem()));
        entity.setWorkingHours(trimNullable(request.getWorkingHours()));
        entity.setOperationType(trimNullable(request.getOperationType()));
        entity.setOperationDuration(trimNullable(request.getOperationDuration()));
        entity.setHazards(trimNullable(request.getHazards()));
        entity.setPpe(trimNullable(request.getPpe()));
        entity.setProtectionFacilities(trimNullable(request.getProtectionFacilities()));
        entity.setNotes(trimNullable(request.getNotes()));
        entity.setDeleted(false);
        return toPositionTemplateResponse(positionTemplateRepository.save(entity));
    }

    @Override
    @Transactional
    public OhdpDto.PositionTemplateResponse updatePositionTemplate(Long id, OhdpDto.PositionTemplateRequest request) {
        OhdpPositionTemplate entity = positionTemplateRepository
                .findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BusinessException("position template not found: " + id));
        entity.setJobName(requireText(request.getJobName(), "jobName"));
        entity.setShiftSystem(trimNullable(request.getShiftSystem()));
        entity.setWorkingHours(trimNullable(request.getWorkingHours()));
        entity.setOperationType(trimNullable(request.getOperationType()));
        entity.setOperationDuration(trimNullable(request.getOperationDuration()));
        entity.setHazards(trimNullable(request.getHazards()));
        entity.setPpe(trimNullable(request.getPpe()));
        entity.setProtectionFacilities(trimNullable(request.getProtectionFacilities()));
        entity.setNotes(trimNullable(request.getNotes()));
        return toPositionTemplateResponse(positionTemplateRepository.save(entity));
    }

    @Override
    @Transactional
    public void deletePositionTemplate(Long id) {
        OhdpPositionTemplate entity = positionTemplateRepository
                .findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BusinessException("position template not found: " + id));
        entity.setDeleted(true);
        positionTemplateRepository.save(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OhdpDto.FactorTemplateResponse> pageFactorTemplates(String keyword, int page, int size) {
        return factorTemplateRepository
                .findByDeletedFalseAndFactorNameContaining(normalize(keyword), pageRequest(page, size))
                .map(this::toFactorTemplateResponse);
    }

    @Override
    @Transactional
    public OhdpDto.FactorTemplateResponse createFactorTemplate(OhdpDto.FactorTemplateRequest request) {
        OhdpFactorTemplate entity = new OhdpFactorTemplate();
        entity.setFactorName(requireText(request.getFactorName(), "factorName"));
        entity.setMetricType(requireText(request.getMetricType(), "metricType").toUpperCase());
        entity.setDefaultUnit(trimNullable(request.getDefaultUnit()));
        entity.setDefaultLimitValue(trimNullable(request.getDefaultLimitValue()));
        entity.setDefaultLimitUnit(trimNullable(request.getDefaultLimitUnit()));
        entity.setNotes(trimNullable(request.getNotes()));
        entity.setDeleted(false);
        return toFactorTemplateResponse(factorTemplateRepository.save(entity));
    }

    @Override
    @Transactional
    public OhdpDto.FactorTemplateResponse updateFactorTemplate(Long id, OhdpDto.FactorTemplateRequest request) {
        OhdpFactorTemplate entity = factorTemplateRepository
                .findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BusinessException("factor template not found: " + id));
        entity.setFactorName(requireText(request.getFactorName(), "factorName"));
        entity.setMetricType(requireText(request.getMetricType(), "metricType").toUpperCase());
        entity.setDefaultUnit(trimNullable(request.getDefaultUnit()));
        entity.setDefaultLimitValue(trimNullable(request.getDefaultLimitValue()));
        entity.setDefaultLimitUnit(trimNullable(request.getDefaultLimitUnit()));
        entity.setNotes(trimNullable(request.getNotes()));
        return toFactorTemplateResponse(factorTemplateRepository.save(entity));
    }

    @Override
    @Transactional
    public void deleteFactorTemplate(Long id) {
        OhdpFactorTemplate entity = factorTemplateRepository
                .findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BusinessException("factor template not found: " + id));
        entity.setDeleted(true);
        factorTemplateRepository.save(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OhdpDto.DictionaryItemResponse> listDictionaryItems(String dictType) {
        String type = requireText(dictType, "dictType");
        return dictionaryItemRepository
                .findByDeletedFalseAndDictTypeOrderBySortNoAscIdAsc(type)
                .stream()
                .map(this::toDictionaryItemResponse)
                .toList();
    }

    @Override
    @Transactional
    public OhdpDto.DictionaryItemResponse createDictionaryItem(OhdpDto.DictionaryItemRequest request) {
        String type = requireText(request.getDictType(), "dictType");
        String code = requireText(request.getItemCode(), "itemCode");
        ensureDictionaryCodeUnique(null, type, code);

        OhdpDictionaryItem entity = new OhdpDictionaryItem();
        entity.setDictType(type);
        entity.setItemCode(code);
        entity.setItemName(requireText(request.getItemName(), "itemName"));
        entity.setSortNo(request.getSortNo() == null ? 0 : request.getSortNo());
        entity.setEnabled(request.getEnabled() == null ? Boolean.TRUE : request.getEnabled());
        entity.setDeleted(false);
        return toDictionaryItemResponse(dictionaryItemRepository.save(entity));
    }

    @Override
    @Transactional
    public OhdpDto.DictionaryItemResponse updateDictionaryItem(Long id, OhdpDto.DictionaryItemRequest request) {
        OhdpDictionaryItem entity = dictionaryItemRepository
                .findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BusinessException("dictionary item not found: " + id));

        String type = requireText(request.getDictType(), "dictType");
        String code = requireText(request.getItemCode(), "itemCode");
        ensureDictionaryCodeUnique(id, type, code);

        entity.setDictType(type);
        entity.setItemCode(code);
        entity.setItemName(requireText(request.getItemName(), "itemName"));
        entity.setSortNo(request.getSortNo() == null ? 0 : request.getSortNo());
        entity.setEnabled(request.getEnabled() == null ? Boolean.TRUE : request.getEnabled());
        return toDictionaryItemResponse(dictionaryItemRepository.save(entity));
    }

    @Override
    @Transactional
    public void deleteDictionaryItem(Long id) {
        OhdpDictionaryItem entity = dictionaryItemRepository
                .findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BusinessException("dictionary item not found: " + id));
        entity.setDeleted(true);
        dictionaryItemRepository.save(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OhdpDto.StaffProfileResponse> pageStaffProfiles(String keyword, int page, int size) {
        return staffProfileRepository
                .findByDeletedFalseAndStaffNameContaining(normalize(keyword), pageRequest(page, size))
                .map(this::toStaffProfileResponse);
    }

    @Override
    @Transactional
    public OhdpDto.StaffProfileResponse createStaffProfile(OhdpDto.StaffProfileRequest request) {
        OhdpStaffProfile entity = new OhdpStaffProfile();
        entity.setStaffName(requireText(request.getStaffName(), "staffName"));
        entity.setStaffTitle(trimNullable(request.getStaffTitle()));
        entity.setCertNo(requireText(request.getCertNo(), "certNo"));
        entity.setContactPhone(trimNullable(request.getContactPhone()));
        entity.setNotes(trimNullable(request.getNotes()));
        entity.setDeleted(false);
        return toStaffProfileResponse(staffProfileRepository.save(entity));
    }

    @Override
    @Transactional
    public OhdpDto.StaffProfileResponse updateStaffProfile(Long id, OhdpDto.StaffProfileRequest request) {
        OhdpStaffProfile entity = staffProfileRepository
                .findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BusinessException("staff profile not found: " + id));
        entity.setStaffName(requireText(request.getStaffName(), "staffName"));
        entity.setStaffTitle(trimNullable(request.getStaffTitle()));
        entity.setCertNo(requireText(request.getCertNo(), "certNo"));
        entity.setContactPhone(trimNullable(request.getContactPhone()));
        entity.setNotes(trimNullable(request.getNotes()));
        return toStaffProfileResponse(staffProfileRepository.save(entity));
    }

    @Override
    @Transactional
    public void deleteStaffProfile(Long id) {
        OhdpStaffProfile entity = staffProfileRepository
                .findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BusinessException("staff profile not found: " + id));
        entity.setDeleted(true);
        staffProfileRepository.save(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OhdpDto.ReportTextTemplateResponse> pageReportTextTemplates(String templateType, String keyword, int page, int size) {
        String type = requireText(templateType, "templateType").toUpperCase();
        return reportTextTemplateRepository
                .findByDeletedFalseAndTemplateTypeAndTemplateNameContaining(type, normalize(keyword), pageRequest(page, size))
                .map(this::toReportTextTemplateResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OhdpDto.ReportTextTemplateResponse> listEnabledReportTextTemplates(String templateType) {
        String type = requireText(templateType, "templateType").toUpperCase();
        return reportTextTemplateRepository.findByDeletedFalseAndTemplateTypeAndEnabledTrueOrderByIdAsc(type).stream()
                .map(this::toReportTextTemplateResponse)
                .toList();
    }

    @Override
    @Transactional
    public OhdpDto.ReportTextTemplateResponse createReportTextTemplate(OhdpDto.ReportTextTemplateRequest request) {
        OhdpReportTextTemplate entity = new OhdpReportTextTemplate();
        entity.setTemplateType(requireText(request.getTemplateType(), "templateType").toUpperCase());
        entity.setTemplateName(requireText(request.getTemplateName(), "templateName"));
        entity.setTemplateContent(requireText(request.getTemplateContent(), "templateContent"));
        entity.setEnabled(request.getEnabled() == null ? Boolean.TRUE : request.getEnabled());
        entity.setDeleted(false);
        return toReportTextTemplateResponse(reportTextTemplateRepository.save(entity));
    }

    @Override
    @Transactional
    public OhdpDto.ReportTextTemplateResponse updateReportTextTemplate(Long id, OhdpDto.ReportTextTemplateRequest request) {
        OhdpReportTextTemplate entity = reportTextTemplateRepository
                .findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BusinessException("report text template not found: " + id));
        entity.setTemplateType(requireText(request.getTemplateType(), "templateType").toUpperCase());
        entity.setTemplateName(requireText(request.getTemplateName(), "templateName"));
        entity.setTemplateContent(requireText(request.getTemplateContent(), "templateContent"));
        entity.setEnabled(request.getEnabled() == null ? Boolean.TRUE : request.getEnabled());
        return toReportTextTemplateResponse(reportTextTemplateRepository.save(entity));
    }

    @Override
    @Transactional
    public void deleteReportTextTemplate(Long id) {
        OhdpReportTextTemplate entity = reportTextTemplateRepository
                .findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BusinessException("report text template not found: " + id));
        entity.setDeleted(true);
        reportTextTemplateRepository.save(entity);
    }

    private OhdpProject getActiveProject(Long id) {
        return projectRepository
                .findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BusinessException("project not found: " + id));
    }

    private void markProjectDataFilled(Long projectId) {
        OhdpProject project = getActiveProject(projectId);
        if (project.getStatus() == ProjectStatus.DRAFT
                || project.getStatus() == ProjectStatus.JUDGED
                || project.getStatus() == ProjectStatus.REPORT_READY) {
            project.setStatus(ProjectStatus.DATA_FILLED);
            projectRepository.save(project);
        }
    }

    private void bindPositionToProject(Long projectId, Long positionId) {
        boolean exists = projectPositionRelRepository.findByProjectIdAndPositionIdAndDeletedFalse(projectId, positionId).isPresent();
        if (exists) {
            return;
        }
        OhdpProjectPositionRel rel = new OhdpProjectPositionRel();
        rel.setProjectId(projectId);
        rel.setPositionId(positionId);
        rel.setDeleted(false);
        projectPositionRelRepository.save(rel);
    }

    private List<OhdpProjectPosition> listProjectPositions(Long projectId) {
        List<OhdpProjectPositionRel> relList = projectPositionRelRepository.findByProjectIdAndDeletedFalseOrderByIdAsc(projectId);
        if (relList.isEmpty()) {
            return List.of();
        }
        List<Long> positionIds = relList.stream().map(OhdpProjectPositionRel::getPositionId).distinct().toList();
        Map<Long, OhdpProjectPosition> map = new LinkedHashMap<>();
        for (OhdpProjectPosition item : positionRepository.findByIdInAndDeletedFalseOrderByIdAsc(positionIds)) {
            map.put(item.getId(), item);
        }
        List<OhdpProjectPosition> positions = new ArrayList<>();
        for (OhdpProjectPositionRel rel : relList) {
            OhdpProjectPosition p = map.get(rel.getPositionId());
            if (p != null) {
                positions.add(p);
            }
        }
        return positions;
    }

    private void applyProjectRequest(OhdpProject project, OhdpDto.ProjectRequest request, boolean createMode) {
        if (request == null) {
            throw new BusinessException("project request is required");
        }
        Long companyProfileId = request.getCompanyProfileId() != null
                ? request.getCompanyProfileId()
                : (createMode ? null : project.getCompanyProfileId());
        Long detectTypeDictItemId = request.getDetectTypeDictItemId() != null
                ? request.getDetectTypeDictItemId()
                : (createMode ? null : project.getDetectTypeDictItemId());
        Long submitUnitCompanyId = request.getSubmitUnitCompanyId() != null
                ? request.getSubmitUnitCompanyId()
                : (createMode ? null : project.getSubmitUnitCompanyId());
        Long entrustUnitCompanyId = request.getEntrustUnitCompanyId() != null
                ? request.getEntrustUnitCompanyId()
                : (createMode ? null : project.getEntrustUnitCompanyId());
        Long taskOriginTemplateId = request.getTaskOriginTemplateId() != null
                ? request.getTaskOriginTemplateId()
                : (createMode ? null : project.getTaskOriginTemplateId());
        Long evaluationBasisTemplateId = request.getEvaluationBasisTemplateId() != null
                ? request.getEvaluationBasisTemplateId()
                : (createMode ? null : project.getEvaluationBasisTemplateId());
        Long writerStaffId = request.getWriterStaffId() != null
                ? request.getWriterStaffId()
                : (createMode ? null : project.getWriterStaffId());
        Long reviewerStaffId = request.getReviewerStaffId() != null
                ? request.getReviewerStaffId()
                : (createMode ? null : project.getReviewerStaffId());
        Long approverStaffId = request.getApproverStaffId() != null
                ? request.getApproverStaffId()
                : (createMode ? null : project.getApproverStaffId());

        OhdpCompanyProfile profile = null;
        if (companyProfileId != null) {
            profile = companyProfileRepository.findByIdAndDeletedFalse(companyProfileId)
                    .orElseThrow(() -> new BusinessException("company profile not found: " + companyProfileId));
        }
        OhdpDictionaryItem detectTypeDict = null;
        if (detectTypeDictItemId != null) {
            detectTypeDict = dictionaryItemRepository.findByIdAndDeletedFalse(detectTypeDictItemId)
                    .orElseThrow(() -> new BusinessException("detect type dictionary not found: " + detectTypeDictItemId));
            if (!Boolean.TRUE.equals(detectTypeDict.getEnabled())) {
                throw new BusinessException("detect type dictionary is disabled");
            }
        }
        OhdpCompanyProfile submitCompany = null;
        if (submitUnitCompanyId != null) {
            submitCompany = companyProfileRepository.findByIdAndDeletedFalse(submitUnitCompanyId)
                    .orElseThrow(() -> new BusinessException("submit unit company not found: " + submitUnitCompanyId));
        }
        OhdpCompanyProfile entrustCompany = null;
        if (entrustUnitCompanyId != null) {
            entrustCompany = companyProfileRepository.findByIdAndDeletedFalse(entrustUnitCompanyId)
                    .orElseThrow(() -> new BusinessException("entrust unit company not found: " + entrustUnitCompanyId));
        }
        OhdpReportTextTemplate taskOriginTemplate = null;
        if (taskOriginTemplateId != null) {
            taskOriginTemplate = reportTextTemplateRepository.findByIdAndDeletedFalse(taskOriginTemplateId)
                    .orElseThrow(() -> new BusinessException("task origin template not found: " + taskOriginTemplateId));
            if (!TEMPLATE_TYPE_TASK_ORIGIN.equalsIgnoreCase(taskOriginTemplate.getTemplateType())) {
                throw new BusinessException("task origin template type mismatch");
            }
        }
        OhdpReportTextTemplate evaluationBasisTemplate = null;
        if (evaluationBasisTemplateId != null) {
            evaluationBasisTemplate = reportTextTemplateRepository.findByIdAndDeletedFalse(evaluationBasisTemplateId)
                    .orElseThrow(() -> new BusinessException("evaluation basis template not found: " + evaluationBasisTemplateId));
            if (!TEMPLATE_TYPE_EVALUATION_BASIS.equalsIgnoreCase(evaluationBasisTemplate.getTemplateType())) {
                throw new BusinessException("evaluation basis template type mismatch");
            }
        }
        OhdpStaffProfile writerStaff = null;
        if (writerStaffId != null) {
            writerStaff = staffProfileRepository.findByIdAndDeletedFalse(writerStaffId)
                    .orElseThrow(() -> new BusinessException("writer staff not found: " + writerStaffId));
        }
        OhdpStaffProfile reviewerStaff = null;
        if (reviewerStaffId != null) {
            reviewerStaff = staffProfileRepository.findByIdAndDeletedFalse(reviewerStaffId)
                    .orElseThrow(() -> new BusinessException("reviewer staff not found: " + reviewerStaffId));
        }
        OhdpStaffProfile approverStaff = null;
        if (approverStaffId != null) {
            approverStaff = staffProfileRepository.findByIdAndDeletedFalse(approverStaffId)
                    .orElseThrow(() -> new BusinessException("approver staff not found: " + approverStaffId));
        }

        String companyName = firstNonBlank(
                trimNullable(request.getCompanyName()),
                profile == null ? null : profile.getCompanyName(),
                createMode ? null : project.getCompanyName());
        String companyAddress = firstNonBlank(
                trimNullable(request.getCompanyAddress()),
                profile == null ? null : profile.getCompanyAddress(),
                createMode ? null : project.getCompanyAddress());
        String detectType = firstNonBlank(
                trimNullable(request.getDetectType()),
                detectTypeDict == null ? null : detectTypeDict.getItemName(),
                createMode ? DEFAULT_DETECT_TYPE : project.getDetectType(),
                DEFAULT_DETECT_TYPE);
        String sampleName = firstNonBlank(
                trimNullable(request.getSampleName()),
                createMode ? DEFAULT_SAMPLE_NAME : project.getSampleName(),
                DEFAULT_SAMPLE_NAME);
        String submitUnit = firstNonBlank(
                submitCompany == null ? null : submitCompany.getCompanyName(),
                createMode ? DEFAULT_SUBMIT_UNIT : project.getSubmitUnit(),
                DEFAULT_SUBMIT_UNIT);
        String entrustUnit = firstNonBlank(
                entrustCompany == null ? null : entrustCompany.getCompanyName(),
                createMode ? DEFAULT_ENTRUST_UNIT : project.getEntrustUnit(),
                DEFAULT_ENTRUST_UNIT);
        String taskOrigin = firstNonBlank(
                taskOriginTemplate == null ? null : taskOriginTemplate.getTemplateContent(),
                createMode ? defaultTaskOrigin(companyName) : project.getTaskOrigin(),
                defaultTaskOrigin(companyName));
        String evaluationBasis = firstNonBlank(
                evaluationBasisTemplate == null ? null : evaluationBasisTemplate.getTemplateContent(),
                createMode ? DEFAULT_EVALUATION_BASIS : project.getEvaluationBasis(),
                DEFAULT_EVALUATION_BASIS);
        String writerCertNo = firstNonBlank(
                writerStaff == null ? null : writerStaff.getCertNo(),
                createMode ? DEFAULT_WRITER_CERT_NO : project.getWriterCertNo(),
                DEFAULT_WRITER_CERT_NO);
        String reviewerCertNo = firstNonBlank(
                reviewerStaff == null ? null : reviewerStaff.getCertNo(),
                createMode ? DEFAULT_REVIEWER_CERT_NO : project.getReviewerCertNo(),
                DEFAULT_REVIEWER_CERT_NO);
        String approverCertNo = firstNonBlank(
                approverStaff == null ? null : approverStaff.getCertNo(),
                createMode ? DEFAULT_APPROVER_CERT_NO : project.getApproverCertNo(),
                DEFAULT_APPROVER_CERT_NO);
        String writerName = firstNonBlank(
                writerStaff == null ? null : writerStaff.getStaffName(),
                createMode ? DEFAULT_WRITER_NAME : project.getWriterName(),
                DEFAULT_WRITER_NAME);
        String reviewerName = firstNonBlank(
                reviewerStaff == null ? null : reviewerStaff.getStaffName(),
                createMode ? DEFAULT_REVIEWER_NAME : project.getReviewerName(),
                DEFAULT_REVIEWER_NAME);
        String approverName = firstNonBlank(
                approverStaff == null ? null : approverStaff.getStaffName(),
                createMode ? DEFAULT_APPROVER_NAME : project.getApproverName(),
                DEFAULT_APPROVER_NAME);

        if (isBlank(companyName)) {
            throw new BusinessException("companyName is required");
        }
        if (isBlank(companyAddress)) {
            throw new BusinessException("companyAddress is required");
        }

        project.setCompanyProfileId(companyProfileId);
        project.setCompanyName(companyName);
        project.setCompanyAddress(companyAddress);
        project.setDetectTypeDictItemId(detectTypeDictItemId);
        project.setDetectType(detectType);
        project.setSamplingDate(request.getSamplingDate() == null ? LocalDate.now() : request.getSamplingDate());
        project.setReportDate(request.getReportDate() == null ? LocalDate.now() : request.getReportDate());
        project.setSampleName(sampleName);
        project.setSubmitUnitCompanyId(submitUnitCompanyId);
        project.setSubmitUnit(submitUnit);
        project.setEntrustUnitCompanyId(entrustUnitCompanyId);
        project.setEntrustUnit(entrustUnit);
        project.setTaskOriginTemplateId(taskOriginTemplateId);
        project.setTaskOrigin(taskOrigin);
        project.setEvaluationBasisTemplateId(evaluationBasisTemplateId);
        project.setEvaluationBasis(evaluationBasis);
        project.setWriterStaffId(writerStaffId);
        project.setWriterName(writerName);
        project.setReviewerStaffId(reviewerStaffId);
        project.setReviewerName(reviewerName);
        project.setApproverStaffId(approverStaffId);
        project.setApproverName(approverName);
        project.setWriterCertNo(writerCertNo);
        project.setReviewerCertNo(reviewerCertNo);
        project.setApproverCertNo(approverCertNo);
        project.setStatementDate(request.getStatementDate() == null ? project.getReportDate() : request.getStatementDate());
        project.setNotes(trimNullable(request.getNotes()));
    }

    private void applyPositionRequest(OhdpProjectPosition position, OhdpDto.PositionRequest request, boolean createMode) {
        if (request == null) {
            throw new BusinessException("position request is required");
        }
        OhdpPositionTemplate template = null;
        if (request.getPositionTemplateId() != null) {
            template = positionTemplateRepository.findByIdAndDeletedFalse(request.getPositionTemplateId())
                    .orElseThrow(() -> new BusinessException("position template not found: " + request.getPositionTemplateId()));
        }

        String jobName = firstNonBlank(
                trimNullable(request.getJobName()),
                template == null ? null : template.getJobName(),
                createMode ? null : position.getJobName());
        if (isBlank(jobName)) {
            throw new BusinessException("jobName is required");
        }

        position.setJobName(jobName);
        position.setShiftSystem(firstNonBlank(
                trimNullable(request.getShiftSystem()),
                template == null ? null : template.getShiftSystem(),
                createMode ? null : position.getShiftSystem()));
        position.setWorkingHours(firstNonBlank(
                trimNullable(request.getWorkingHours()),
                template == null ? null : template.getWorkingHours(),
                createMode ? null : position.getWorkingHours()));
        position.setWorkerCount(request.getWorkerCount() != null
                ? request.getWorkerCount()
                : (createMode ? 1 : position.getWorkerCount()));
        position.setWorkLocation(trimNullable(request.getWorkLocation()));
        position.setOperationType(firstNonBlank(
                trimNullable(request.getOperationType()),
                template == null ? null : template.getOperationType(),
                createMode ? null : position.getOperationType()));
        position.setOperationContent(trimNullable(request.getOperationContent()));
        position.setOperationDuration(firstNonBlank(
                trimNullable(request.getOperationDuration()),
                template == null ? null : template.getOperationDuration(),
                createMode ? null : position.getOperationDuration()));
        position.setHazards(firstNonBlank(
                trimNullable(request.getHazards()),
                template == null ? null : template.getHazards(),
                createMode ? null : position.getHazards()));
        position.setPpe(firstNonBlank(
                trimNullable(request.getPpe()),
                template == null ? null : template.getPpe(),
                createMode ? null : position.getPpe()));
        position.setProtectionFacilities(firstNonBlank(
                trimNullable(request.getProtectionFacilities()),
                template == null ? null : template.getProtectionFacilities(),
                createMode ? null : position.getProtectionFacilities()));
    }

    private void applyResultRequest(OhdpResultItem result, OhdpDto.ResultItemRequest request, boolean createMode) {
        if (request == null) {
            throw new BusinessException("result request is required");
        }
        OhdpProjectPosition position = null;
        if (request.getPositionId() != null) {
            position = positionRepository.findByIdAndDeletedFalse(request.getPositionId())
                    .orElseThrow(() -> new BusinessException("project position not found: " + request.getPositionId()));
            boolean linked = projectPositionRelRepository
                    .findByProjectIdAndPositionIdAndDeletedFalse(result.getProjectId(), position.getId())
                    .isPresent();
            if (!linked) {
                throw new BusinessException("position does not belong to current project");
            }
        }
        OhdpFactorTemplate template = null;
        if (request.getFactorTemplateId() != null) {
            template = factorTemplateRepository.findByIdAndDeletedFalse(request.getFactorTemplateId())
                    .orElseThrow(() -> new BusinessException("factor template not found: " + request.getFactorTemplateId()));
        }

        String jobName = firstNonBlank(
                trimNullable(request.getJobName()),
                position == null ? null : position.getJobName(),
                createMode ? null : result.getJobName());
        String factorName = firstNonBlank(
                trimNullable(request.getFactorName()),
                template == null ? null : template.getFactorName(),
                createMode ? null : result.getFactorName());
        String metricType = firstNonBlank(
                trimNullable(request.getMetricType()),
                template == null ? null : template.getMetricType(),
                createMode ? null : result.getMetricType());
        if (isBlank(jobName)) {
            throw new BusinessException("jobName is required");
        }
        if (isBlank(factorName)) {
            throw new BusinessException("factorName is required");
        }
        if (isBlank(metricType)) {
            throw new BusinessException("metricType is required");
        }
        if (request.getPositionId() == null && result.getPositionId() == null) {
            throw new BusinessException("positionId is required");
        }

        BigDecimal limitValue = request.getLimitValue();
        if (limitValue == null && template != null) {
            limitValue = parseDecimal(template.getDefaultLimitValue());
        }
        if (limitValue == null && !createMode) {
            limitValue = result.getLimitValue();
        }

        result.setPositionId(request.getPositionId() == null ? result.getPositionId() : request.getPositionId());
        result.setJobName(jobName);
        result.setFactorName(factorName);
        result.setMetricType(metricType.toUpperCase());
        result.setValueRaw(trimNullable(request.getValueRaw()));
        result.setValueNum(request.getValueNum() == null && !createMode ? result.getValueNum() : request.getValueNum());
        result.setUnit(firstNonBlank(
                trimNullable(request.getUnit()),
                template == null ? null : trimNullable(template.getDefaultUnit()),
                createMode ? null : result.getUnit()));
        result.setLimitValue(limitValue);
        result.setLimitUnit(firstNonBlank(
                trimNullable(request.getLimitUnit()),
                template == null ? null : trimNullable(template.getDefaultLimitUnit()),
                createMode ? null : result.getLimitUnit()));

        Long uniquePositionId = result.getPositionId();
        if (uniquePositionId != null) {
            boolean duplicated = createMode
                    ? resultItemRepository.existsByProjectIdAndPositionIdAndFactorNameAndMetricTypeAndDeletedFalse(
                    result.getProjectId(), uniquePositionId, result.getFactorName(), result.getMetricType())
                    : resultItemRepository.existsByProjectIdAndPositionIdAndFactorNameAndMetricTypeAndDeletedFalseAndIdNot(
                    result.getProjectId(), uniquePositionId, result.getFactorName(), result.getMetricType(), result.getId());
            if (duplicated) {
                throw new BusinessException("duplicate result item: same project + position + factor + metricType");
            }
        }
    }

    private String evaluate(OhdpResultItem item) {
        BigDecimal limitValue = item.getLimitValue();
        if (limitValue == null) {
            return JUDGE_PENDING;
        }
        BigDecimal value = item.getValueNum();
        if (value == null) {
            value = extractNumeric(item.getValueRaw());
        }
        if (value == null) {
            return JUDGE_PENDING;
        }
        return value.compareTo(limitValue) <= 0 ? JUDGE_PASS : JUDGE_FAIL;
    }

    private BigDecimal extractNumeric(String valueRaw) {
        if (valueRaw == null || valueRaw.isBlank()) {
            return null;
        }
        Matcher matcher = NUMBER_PATTERN.matcher(valueRaw);
        if (!matcher.find()) {
            return null;
        }
        return new BigDecimal(matcher.group());
    }

    private String generateProjectCode() {
        for (int i = 0; i < 30; i++) {
            String code = "OHDP-" + LocalDateTime.now().format(CODE_TIME_FORMAT) + "-" + (100 + RANDOM.nextInt(900));
            if (!projectRepository.existsByProjectCodeAndDeletedFalse(code)) {
                return code;
            }
        }
        throw new BusinessException("failed to generate project code");
    }

    private void writeReportDocument(
            Path filePath,
            OhdpProject project,
            List<OhdpProjectPosition> positions,
            List<OhdpResultItem> results) {
        Path tempFile = null;
        Map<String, String> replacements = buildTemplateReplacements(project, positions, results);
        if (writeReportByWordAutomation(filePath, replacements)) {
            return;
        }
        try {
            tempFile = Files.createTempFile(filePath.getParent(), "ohdp-report-", ".doc");
            try (InputStream templateStream = new ClassPathResource(REPORT_TEMPLATE_PATH).getInputStream();
                 HWPFDocument document = new HWPFDocument(templateStream);
                 OutputStream outputStream = Files.newOutputStream(tempFile)) {
                Range range = document.getRange();
                applyTemplateReplacements(range, replacements);
                appendReportSummary(range, project, positions, results);
                document.write(outputStream);
            }
            if (!isReadableDocFile(tempFile)) {
                if (!writeReportByWordAutomation(filePath, replacements)) {
                    copyTemplateAsFallback(filePath);
                }
            } else {
                Files.move(tempFile, filePath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new BusinessException("failed to generate template report document");
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ignored) {
                }
            }
        }
    }

    private boolean writeReportByWordAutomation(Path outputFile, Map<String, String> replacements) {
        if (!System.getProperty("os.name", "").toLowerCase().contains("win")) {
            return false;
        }
        Path templateFile = null;
        Path jsonFile = null;
        Path scriptFile = null;
        try {
            templateFile = Files.createTempFile(outputFile.getParent(), "ohdp-template-", ".doc");
            try (InputStream templateStream = new ClassPathResource(REPORT_TEMPLATE_PATH).getInputStream()) {
                Files.copy(templateStream, templateFile, StandardCopyOption.REPLACE_EXISTING);
            }

            jsonFile = Files.createTempFile(outputFile.getParent(), "ohdp-replacements-", ".json");
            Files.writeString(jsonFile, OBJECT_MAPPER.writeValueAsString(replacements), StandardCharsets.UTF_8);

            scriptFile = Files.createTempFile(outputFile.getParent(), "ohdp-word-", ".ps1");
            Files.writeString(scriptFile, WORD_REPLACE_SCRIPT, StandardCharsets.UTF_8);

            ProcessBuilder processBuilder = new ProcessBuilder(
                    resolvePowerShellExecutable(),
                    "-NoProfile",
                    "-ExecutionPolicy",
                    "Bypass",
                    "-File",
                    scriptFile.toString(),
                    templateFile.toString(),
                    outputFile.toString(),
                    jsonFile.toString());
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();
            process.getInputStream().readAllBytes();
            int exitCode = process.waitFor();
            return exitCode == 0 && Files.exists(outputFile) && isReadableDocFile(outputFile);
        } catch (Exception ex) {
            return false;
        } finally {
            tryDelete(templateFile);
            tryDelete(jsonFile);
            tryDelete(scriptFile);
        }
    }

    private void tryDelete(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
        }
    }

    private String resolvePowerShellExecutable() {
        String[] candidates = new String[] {
                "C:\\\\Windows\\\\System32\\\\WindowsPowerShell\\\\v1.0\\\\powershell.exe",
                "C:\\\\Windows\\\\SysWOW64\\\\WindowsPowerShell\\\\v1.0\\\\powershell.exe"
        };
        for (String candidate : candidates) {
            if (Files.exists(Paths.get(candidate))) {
                return candidate;
            }
        }
        return "powershell";
    }

    private boolean isReadableDocFile(Path filePath) {
        try (InputStream in = Files.newInputStream(filePath);
             HWPFDocument document = new HWPFDocument(in)) {
            String text = document.getRange().text();
            return text != null && text.length() > 100;
        } catch (Exception ex) {
            return false;
        }
    }

    private void copyTemplateAsFallback(Path filePath) throws IOException {
        try (InputStream templateStream = new ClassPathResource(REPORT_TEMPLATE_PATH).getInputStream()) {
            Files.copy(templateStream, filePath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private Path resolveReportStorageDir() {
        String storageDir = reportProperties.getStorageDir();
        if (storageDir == null || storageDir.isBlank()) {
            throw new BusinessException("report storage dir is not configured");
        }
        Path dir = Paths.get(storageDir);
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new BusinessException("failed to prepare report storage directory");
        }
        return dir;
    }

    private void cleanupPreviewFiles(Path previewDir) {
        long cutoff = System.currentTimeMillis() - PREVIEW_RETENTION_MILLIS;
        try (var files = Files.list(previewDir)) {
            files.filter(Files::isRegularFile).forEach(path -> {
                try {
                    if (Files.getLastModifiedTime(path).toMillis() < cutoff) {
                        Files.deleteIfExists(path);
                    }
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }

    private Map<String, String> buildTemplateReplacements(
            OhdpProject project,
            List<OhdpProjectPosition> positions,
            List<OhdpResultItem> results) {
        Map<String, String> replacements = new LinkedHashMap<>();
        replacements.put(TEMPLATE_PROJECT_CODE, valueOf(project.getProjectCode()));
        replacements.put(TEMPLATE_PROJECT_CODE_ALT, valueOf(project.getProjectCode()));
        replacements.put(TEMPLATE_COMPANY_A, valueOf(project.getCompanyName()));
        replacements.put(TEMPLATE_COMPANY_B, valueOf(project.getCompanyName()));
        replacements.put(TEMPLATE_COMPANY_C, valueOf(project.getCompanyName()));
        replacements.put(TEMPLATE_COMPANY_D, valueOf(project.getCompanyName()));
        replacements.put(TEMPLATE_COMPANY_ALT, valueOf(project.getCompanyName()));
        replacements.put(TEMPLATE_COMPANY_C_ALT, valueOf(project.getCompanyName()));
        replacements.put(TEMPLATE_STATEMENT_COMPANY, valueOf(project.getCompanyName()));
        replacements.put(TEMPLATE_COMPANY_ADDRESS, valueOf(project.getCompanyAddress()));
        replacements.put(TEMPLATE_COMPANY_ADDRESS_ALT, valueOf(project.getCompanyAddress()));
        replacements.put(TEMPLATE_DETECT_TYPE, valueOf(project.getDetectType()));
        replacements.put(TEMPLATE_SAMPLING_DATE, formatChineseDate(project.getSamplingDate()));
        replacements.put(TEMPLATE_REPORT_DATE, formatChineseDate(project.getReportDate()));
        replacements.put(TEMPLATE_SAMPLE_NAME, valueOf(project.getSampleName()));
        replacements.put(TEMPLATE_SAMPLE_NAME_ALT, valueOf(project.getSampleName()));
        replacements.put("{{POSITION_COUNT}}", String.valueOf(positions.size()));
        replacements.put("{{RESULT_COUNT}}", String.valueOf(results.size()));
        replacements.put("__APPEND_SUMMARY__", buildSummaryText(project, positions, results));
        return replacements;
    }

    private void applyTemplateReplacements(Range range, Map<String, String> replacements) {
        replacements.entrySet().stream()
                .sorted(Comparator.comparingInt((Map.Entry<String, String> e) -> e.getKey().length()).reversed())
                .forEach(entry -> {
                    String source = entry.getKey();
                    if (source.startsWith("__")) {
                        return;
                    }
                    String target = valueOf(entry.getValue());
                    if (!source.equals(target)) {
                        range.replaceText(source, target);
                    }
                });
    }

    private void appendReportSummary(
            Range range,
            OhdpProject project,
            List<OhdpProjectPosition> positions,
            List<OhdpResultItem> results) {
        range.insertAfter(buildSummaryText(project, positions, results));
    }

    private String buildSummaryText(
            OhdpProject project,
            List<OhdpProjectPosition> positions,
            List<OhdpResultItem> results) {
        StringBuilder builder = new StringBuilder();
        builder.append("\r\n\r\n[SYSTEM INPUT]\r\n");
        builder.append("ProjectCode: ").append(valueOf(project.getProjectCode())).append("\r\n");
        builder.append("Company: ").append(valueOf(project.getCompanyName())).append("\r\n");
        builder.append("Address: ").append(valueOf(project.getCompanyAddress())).append("\r\n");
        builder.append("DetectType: ").append(valueOf(project.getDetectType())).append("\r\n");
        builder.append("SampleName: ").append(valueOf(project.getSampleName())).append("\r\n");
        builder.append("SubmitUnit: ").append(valueOf(project.getSubmitUnit())).append("\r\n");
        builder.append("EntrustUnit: ").append(valueOf(project.getEntrustUnit())).append("\r\n");
        builder.append("Writer: ").append(valueOf(project.getWriterName())).append("\r\n");
        builder.append("Reviewer: ").append(valueOf(project.getReviewerName())).append("\r\n");
        builder.append("Approver: ").append(valueOf(project.getApproverName())).append("\r\n");
        builder.append("PositionCount: ").append(positions.size()).append("; ResultCount: ").append(results.size()).append("\r\n");
        builder.append("TaskOrigin: ").append(valueOf(project.getTaskOrigin())).append("\r\n");
        builder.append("EvaluationBasis: ").append(valueOf(project.getEvaluationBasis())).append("\r\n");
        return builder.toString();
    }

    private String formatChineseDate(LocalDate date) {
        return date == null ? "-" : CHINESE_DATE_FORMAT.format(date);
    }

    private void fillDefaultReportFields(OhdpProject project) {
        if (isBlank(project.getSampleName())) {
            project.setSampleName(DEFAULT_SAMPLE_NAME);
        }
        if (isBlank(project.getSubmitUnit())) {
            project.setSubmitUnit(DEFAULT_SUBMIT_UNIT);
        }
        if (isBlank(project.getEntrustUnit())) {
            project.setEntrustUnit(DEFAULT_ENTRUST_UNIT);
        }
        if (isBlank(project.getTaskOrigin())) {
            project.setTaskOrigin(defaultTaskOrigin(project.getCompanyName()));
        }
        if (isBlank(project.getEvaluationBasis())) {
            project.setEvaluationBasis(DEFAULT_EVALUATION_BASIS);
        }
        if (isBlank(project.getWriterName())) {
            project.setWriterName(DEFAULT_WRITER_NAME);
        }
        if (isBlank(project.getReviewerName())) {
            project.setReviewerName(DEFAULT_REVIEWER_NAME);
        }
        if (isBlank(project.getApproverName())) {
            project.setApproverName(DEFAULT_APPROVER_NAME);
        }
        if (isBlank(project.getWriterCertNo())) {
            project.setWriterCertNo(DEFAULT_WRITER_CERT_NO);
        }
        if (isBlank(project.getReviewerCertNo())) {
            project.setReviewerCertNo(DEFAULT_REVIEWER_CERT_NO);
        }
        if (isBlank(project.getApproverCertNo())) {
            project.setApproverCertNo(DEFAULT_APPROVER_CERT_NO);
        }
        if (project.getStatementDate() == null) {
            project.setStatementDate(project.getReportDate() == null ? LocalDate.now() : project.getReportDate());
        }
    }

    private String defaultTaskOrigin(String companyName) {
        String unitName = isBlank(companyName) ? "\u53d7\u68c0\u5355\u4f4d" : companyName;
        return "\u6839\u636e\u81ea\u6cbb\u533a\u804c\u4e1a\u5065\u5eb7\u76f8\u5173\u76d1\u6d4b\u4efb\u52a1\u8981\u6c42\uff0c"
                + "\u4e3a\u843d\u5b9e\u91cd\u70b9\u884c\u4e1a\u804c\u4e1a\u75c5\u5371\u5bb3\u56e0\u7d20\u76d1\u6d4b\u5de5\u4f5c\uff0c"
                + "\u5317\u6d77\u5e02\u75be\u75c5\u9884\u9632\u63a7\u5236\u4e2d\u5fc3\uff08\u5317\u6d77\u5e02\u536b\u751f\u76d1\u7763\u6240\uff09\u5bf9"
                + unitName
                + "\u5b58\u5728\u804c\u4e1a\u75c5\u5371\u5bb3\u56e0\u7d20\u7684\u90e8\u5206\u5de5\u4f5c\u573a\u6240\u548c\u5c97\u4f4d\u5f00\u5c55\u62bd\u6837\u68c0\u6d4b\u3002";
    }

    private OhdpDto.ReportPrecheckResponse buildReportPrecheck(Long projectId, OhdpProject project) {
        List<String> missingItems = new ArrayList<>();
        if (isBlank(project.getCompanyName())) {
            missingItems.add("project.companyName");
        } else if (MOJIBAKE_PATTERN.matcher(project.getCompanyName()).find()) {
            missingItems.add("project.companyName.garbled");
        }
        if (isBlank(project.getCompanyAddress())) {
            missingItems.add("project.companyAddress");
        } else if (MOJIBAKE_PATTERN.matcher(project.getCompanyAddress()).find()) {
            missingItems.add("project.companyAddress.garbled");
        }
        if (isBlank(project.getDetectType())) {
            missingItems.add("project.detectType");
        } else if (MOJIBAKE_PATTERN.matcher(project.getDetectType()).find()) {
            missingItems.add("project.detectType.garbled");
        }
        if (project.getSamplingDate() == null) {
            missingItems.add("project.samplingDate");
        }
        if (project.getReportDate() == null) {
            missingItems.add("project.reportDate");
        }

        List<OhdpProjectPosition> positions = listProjectPositions(projectId);
        if (positions.isEmpty()) {
            missingItems.add("positions.empty");
        }
        List<OhdpResultItem> results = resultItemRepository.findByProjectIdAndDeletedFalseOrderByIdAsc(projectId);
        if (results.isEmpty()) {
            missingItems.add("results.empty");
        }
        if (results.stream().anyMatch(item -> isBlank(item.getJobName()) || isBlank(item.getFactorName()) || isBlank(item.getMetricType()))) {
            missingItems.add("results.requiredFields");
        }
        if (positions.stream().anyMatch(item ->
                containsMojibake(item.getJobName())
                        || containsMojibake(item.getHazards())
                        || containsMojibake(item.getOperationType()))) {
            missingItems.add("positions.garbled");
        }
        if (results.stream().anyMatch(item ->
                containsMojibake(item.getJobName())
                        || containsMojibake(item.getFactorName())
                        || containsMojibake(item.getMetricType())
                        || containsMojibake(item.getJudgement()))) {
            missingItems.add("results.garbled");
        }
        return OhdpDto.ReportPrecheckResponse.builder()
                .projectId(projectId)
                .passed(missingItems.isEmpty())
                .missingItems(missingItems)
                .build();
    }

    private void logReportOperation(Long projectId, String operationType, String message) {
        OhdpReportOperationLog log = new OhdpReportOperationLog();
        log.setProjectId(projectId);
        log.setOperationType(operationType);
        log.setOperatorName(currentUsername());
        log.setOperationMessage(message);
        log.setOperatedAt(LocalDateTime.now());
        log.setDeleted(false);
        reportOperationLogRepository.save(log);
    }

    private String currentUsername() {
        AuthenticatedUser user = AuthContext.get();
        return user == null ? "system" : user.getUsername();
    }

    private Pageable pageRequest(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.max(size, 1), Sort.by(Sort.Direction.DESC, "id"));
    }

    private void ensureDictionaryCodeUnique(Long currentId, String dictType, String itemCode) {
        boolean duplicated = dictionaryItemRepository.findByDeletedFalseAndDictTypeOrderBySortNoAscIdAsc(dictType).stream()
                .anyMatch(item -> itemCode.equalsIgnoreCase(item.getItemCode())
                        && (currentId == null || !Objects.equals(currentId, item.getId())));
        if (duplicated) {
            throw new BusinessException("dictionary item code already exists");
        }
    }

    private BigDecimal parseDecimal(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String normalize(String text) {
        return text == null ? "" : text.trim();
    }

    private String trimNullable(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        ensureNoMojibake(trimmed);
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void ensureNoMojibake(String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        if (MOJIBAKE_PATTERN.matcher(text).find()) {
            throw new BusinessException("input contains garbled text, please use UTF-8 and re-enter");
        }
    }

    private String requireText(String text, String fieldName) {
        String value = trimNullable(text);
        if (value == null) {
            throw new BusinessException(fieldName + " is required");
        }
        return value;
    }

    private String firstNonBlank(String... candidates) {
        for (String candidate : candidates) {
            if (!isBlank(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String valueOf(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private boolean containsMojibake(String value) {
        return value != null && !value.isBlank() && MOJIBAKE_PATTERN.matcher(value).find();
    }

    private OhdpDto.ProjectResponse toProjectResponse(OhdpProject project) {
        return OhdpDto.ProjectResponse.builder()
                .id(project.getId())
                .projectCode(project.getProjectCode())
                .companyProfileId(project.getCompanyProfileId())
                .companyName(project.getCompanyName())
                .companyAddress(project.getCompanyAddress())
                .detectTypeDictItemId(project.getDetectTypeDictItemId())
                .detectType(project.getDetectType())
                .samplingDate(project.getSamplingDate())
                .reportDate(project.getReportDate())
                .sampleName(project.getSampleName())
                .submitUnitCompanyId(project.getSubmitUnitCompanyId())
                .submitUnit(project.getSubmitUnit())
                .entrustUnitCompanyId(project.getEntrustUnitCompanyId())
                .entrustUnit(project.getEntrustUnit())
                .taskOriginTemplateId(project.getTaskOriginTemplateId())
                .taskOrigin(project.getTaskOrigin())
                .evaluationBasisTemplateId(project.getEvaluationBasisTemplateId())
                .evaluationBasis(project.getEvaluationBasis())
                .writerStaffId(project.getWriterStaffId())
                .writerName(project.getWriterName())
                .reviewerStaffId(project.getReviewerStaffId())
                .reviewerName(project.getReviewerName())
                .approverStaffId(project.getApproverStaffId())
                .approverName(project.getApproverName())
                .writerCertNo(project.getWriterCertNo())
                .reviewerCertNo(project.getReviewerCertNo())
                .approverCertNo(project.getApproverCertNo())
                .statementDate(project.getStatementDate())
                .notes(project.getNotes())
                .status(project.getStatus())
                .createdAt(project.getCreatedAt())
                .updatedAt(project.getUpdatedAt())
                .build();
    }

    private OhdpDto.PositionResponse toPositionResponse(OhdpProjectPosition position, Long projectId) {
        return OhdpDto.PositionResponse.builder()
                .id(position.getId())
                .projectId(projectId)
                .jobName(position.getJobName())
                .shiftSystem(position.getShiftSystem())
                .workingHours(position.getWorkingHours())
                .workerCount(position.getWorkerCount())
                .workLocation(position.getWorkLocation())
                .operationType(position.getOperationType())
                .operationContent(position.getOperationContent())
                .operationDuration(position.getOperationDuration())
                .hazards(position.getHazards())
                .ppe(position.getPpe())
                .protectionFacilities(position.getProtectionFacilities())
                .build();
    }

    private OhdpDto.ResultItemResponse toResultResponse(OhdpResultItem item) {
        return OhdpDto.ResultItemResponse.builder()
                .id(item.getId())
                .projectId(item.getProjectId())
                .positionId(item.getPositionId())
                .jobName(item.getJobName())
                .factorName(item.getFactorName())
                .metricType(item.getMetricType())
                .valueRaw(item.getValueRaw())
                .valueNum(item.getValueNum())
                .unit(item.getUnit())
                .limitValue(item.getLimitValue())
                .limitUnit(item.getLimitUnit())
                .judgement(item.getJudgement())
                .build();
    }

    private OhdpDto.ReportFileResponse toReportResponse(OhdpReportFile reportFile) {
        return OhdpDto.ReportFileResponse.builder()
                .id(reportFile.getId())
                .projectId(reportFile.getProjectId())
                .fileName(reportFile.getFileName())
                .filePath(reportFile.getFilePath())
                .templateVersion(reportFile.getTemplateVersion())
                .generatedBy(reportFile.getGeneratedBy())
                .generatedAt(reportFile.getGeneratedAt())
                .build();
    }

    private OhdpDto.ReportOperationLogResponse toReportOperationLogResponse(OhdpReportOperationLog log) {
        return OhdpDto.ReportOperationLogResponse.builder()
                .id(log.getId())
                .projectId(log.getProjectId())
                .operationType(log.getOperationType())
                .operatorName(log.getOperatorName())
                .operationMessage(log.getOperationMessage())
                .operatedAt(log.getOperatedAt())
                .build();
    }

    private OhdpDto.CompanyProfileResponse toCompanyProfileResponse(OhdpCompanyProfile entity) {
        return OhdpDto.CompanyProfileResponse.builder()
                .id(entity.getId())
                .companyName(entity.getCompanyName())
                .companyAddress(entity.getCompanyAddress())
                .contactName(entity.getContactName())
                .contactPhone(entity.getContactPhone())
                .industryType(entity.getIndustryType())
                .notes(entity.getNotes())
                .build();
    }

    private OhdpDto.PositionTemplateResponse toPositionTemplateResponse(OhdpPositionTemplate entity) {
        return OhdpDto.PositionTemplateResponse.builder()
                .id(entity.getId())
                .jobName(entity.getJobName())
                .shiftSystem(entity.getShiftSystem())
                .workingHours(entity.getWorkingHours())
                .operationType(entity.getOperationType())
                .operationDuration(entity.getOperationDuration())
                .hazards(entity.getHazards())
                .ppe(entity.getPpe())
                .protectionFacilities(entity.getProtectionFacilities())
                .notes(entity.getNotes())
                .build();
    }

    private OhdpDto.FactorTemplateResponse toFactorTemplateResponse(OhdpFactorTemplate entity) {
        return OhdpDto.FactorTemplateResponse.builder()
                .id(entity.getId())
                .factorName(entity.getFactorName())
                .metricType(entity.getMetricType())
                .defaultUnit(entity.getDefaultUnit())
                .defaultLimitValue(entity.getDefaultLimitValue())
                .defaultLimitUnit(entity.getDefaultLimitUnit())
                .notes(entity.getNotes())
                .build();
    }

    private OhdpDto.DictionaryItemResponse toDictionaryItemResponse(OhdpDictionaryItem entity) {
        return OhdpDto.DictionaryItemResponse.builder()
                .id(entity.getId())
                .dictType(entity.getDictType())
                .itemCode(entity.getItemCode())
                .itemName(entity.getItemName())
                .sortNo(entity.getSortNo())
                .enabled(entity.getEnabled())
                .build();
    }

    private OhdpDto.StaffProfileResponse toStaffProfileResponse(OhdpStaffProfile entity) {
        return OhdpDto.StaffProfileResponse.builder()
                .id(entity.getId())
                .staffName(entity.getStaffName())
                .staffTitle(entity.getStaffTitle())
                .certNo(entity.getCertNo())
                .contactPhone(entity.getContactPhone())
                .notes(entity.getNotes())
                .build();
    }

    private OhdpDto.ReportTextTemplateResponse toReportTextTemplateResponse(OhdpReportTextTemplate entity) {
        return OhdpDto.ReportTextTemplateResponse.builder()
                .id(entity.getId())
                .templateType(entity.getTemplateType())
                .templateName(entity.getTemplateName())
                .templateContent(entity.getTemplateContent())
                .enabled(entity.getEnabled())
                .build();
    }
}

