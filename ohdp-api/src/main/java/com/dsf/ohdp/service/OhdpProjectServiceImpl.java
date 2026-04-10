package com.dsf.ohdp.service;

import com.dsf.auth.security.AuthContext;
import com.dsf.auth.security.AuthenticatedUser;
import com.dsf.common.exception.BusinessException;
import com.dsf.ohdp.config.OhdpReportProperties;
import com.dsf.ohdp.dto.OhdpDto;
import com.dsf.ohdp.entity.OhdpCompanyProfile;
import com.dsf.ohdp.entity.OhdpDictionaryItem;
import com.dsf.ohdp.entity.OhdpFactorTemplate;
import com.dsf.ohdp.entity.OhdpPositionTemplate;
import com.dsf.ohdp.entity.OhdpProject;
import com.dsf.ohdp.entity.OhdpProjectPosition;
import com.dsf.ohdp.entity.OhdpReportFile;
import com.dsf.ohdp.entity.OhdpReportOperationLog;
import com.dsf.ohdp.entity.OhdpResultItem;
import com.dsf.ohdp.entity.ProjectStatus;
import com.dsf.ohdp.repository.OhdpCompanyProfileRepository;
import com.dsf.ohdp.repository.OhdpDictionaryItemRepository;
import com.dsf.ohdp.repository.OhdpFactorTemplateRepository;
import com.dsf.ohdp.repository.OhdpPositionTemplateRepository;
import com.dsf.ohdp.repository.OhdpProjectPositionRepository;
import com.dsf.ohdp.repository.OhdpProjectRepository;
import com.dsf.ohdp.repository.OhdpReportFileRepository;
import com.dsf.ohdp.repository.OhdpReportOperationLogRepository;
import com.dsf.ohdp.repository.OhdpResultItemRepository;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
    private static final String REPORT_TEMPLATE_PATH = "templates/occupational_report_template.doc";
    private static final Random RANDOM = new Random();

    private static final String JUDGE_PENDING = "\u672a\u5224\u5b9a";
    private static final String JUDGE_PASS = "\u5408\u683c";
    private static final String JUDGE_FAIL = "\u4e0d\u5408\u683c";
    private static final String DEFAULT_DETECT_TYPE = "\u76d1\u6d4b\u68c0\u9a8c";

    private static final String TEMPLATE_PROJECT_CODE = "\u804c202500XX";
    private static final String TEMPLATE_COMPANY_A = "XXXXXXXXXXXXXX\u516c\u53f8";
    private static final String TEMPLATE_COMPANY_B = "XXXXXXXXXXXX\u516c\u53f8";
    private static final String TEMPLATE_COMPANY_C = "XXXXXXXXXXX\u516c\u53f8";
    private static final String TEMPLATE_COMPANY_D = "XXXXXX\u6709\u9650\u516c\u53f8";
    private static final String TEMPLATE_COMPANY_ADDRESS = "\u5317\u6d77\u5e02XXXXXXXXXXXXXXXXXX";
    private static final String TEMPLATE_DETECT_TYPE = "\u76d1\u6d4b\u68c0\u9a8c";
    private static final String TEMPLATE_SAMPLING_DATE = "2025\u5e748\u670821\u65e5";
    private static final String TEMPLATE_REPORT_DATE = "2025\u5e7412\u67084\u65e5";

    private static final String REPORT_TEMPLATE_VERSION = "WORD-TEMPLATE-V2";
    private static final String REPORT_FILE_SUFFIX = "_report.doc";
    private static final String PREVIEW_FILE_PREFIX = "preview_";
    private static final long PREVIEW_RETENTION_MILLIS = 6L * 60 * 60 * 1000;

    private final OhdpProjectRepository projectRepository;
    private final OhdpProjectPositionRepository positionRepository;
    private final OhdpResultItemRepository resultItemRepository;
    private final OhdpReportFileRepository reportFileRepository;
    private final OhdpReportOperationLogRepository reportOperationLogRepository;
    private final OhdpCompanyProfileRepository companyProfileRepository;
    private final OhdpPositionTemplateRepository positionTemplateRepository;
    private final OhdpFactorTemplateRepository factorTemplateRepository;
    private final OhdpDictionaryItemRepository dictionaryItemRepository;
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
                Sort.by(Sort.Direction.DESC, "id"));

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
        position.setProjectId(projectId);
        position.setDeleted(false);
        applyPositionRequest(position, request, true);
        OhdpProjectPosition saved = positionRepository.save(position);
        markProjectDataFilled(projectId);
        return toPositionResponse(saved);
    }

    @Override
    @Transactional
    public OhdpDto.PositionResponse updatePosition(Long positionId, OhdpDto.PositionRequest request) {
        OhdpProjectPosition position = positionRepository
                .findByIdAndDeletedFalse(positionId)
                .orElseThrow(() -> new BusinessException("position not found: " + positionId));
        applyPositionRequest(position, request, false);
        OhdpProjectPosition saved = positionRepository.save(position);
        markProjectDataFilled(position.getProjectId());
        return toPositionResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OhdpDto.PositionResponse> listPositions(Long projectId) {
        getActiveProject(projectId);
        return positionRepository.findByProjectIdAndDeletedFalseOrderByIdAsc(projectId)
                .stream()
                .map(this::toPositionResponse)
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
        OhdpDto.ReportPrecheckResponse precheck = buildReportPrecheck(projectId, project);
        if (!Boolean.TRUE.equals(precheck.getPassed())) {
            throw new BusinessException("report precheck failed: " + String.join("; ", precheck.getMissingItems()));
        }

        Path dir = resolveReportStorageDir();

        String fileName = project.getProjectCode() + REPORT_FILE_SUFFIX;
        Path filePath = dir.resolve(fileName);

        List<OhdpProjectPosition> positions = positionRepository.findByProjectIdAndDeletedFalseOrderByIdAsc(projectId);
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
        List<OhdpDto.PositionResponse> positions = positionRepository.findByProjectIdAndDeletedFalseOrderByIdAsc(projectId)
                .stream()
                .map(this::toPositionResponse)
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
        copied.setCompanyName(source.getCompanyName());
        copied.setCompanyAddress(source.getCompanyAddress());
        copied.setDetectType(source.getDetectType());
        copied.setSamplingDate(source.getSamplingDate());
        copied.setReportDate(source.getReportDate());
        copied.setNotes(source.getNotes());
        copied.setStatus(ProjectStatus.DRAFT);
        copied.setDeleted(false);
        copied = projectRepository.save(copied);

        List<OhdpProjectPosition> sourcePositions = positionRepository.findByProjectIdAndDeletedFalseOrderByIdAsc(sourceProjectId);
        if (!sourcePositions.isEmpty()) {
            List<OhdpProjectPosition> copiedPositions = new ArrayList<>();
            for (OhdpProjectPosition src : sourcePositions) {
                OhdpProjectPosition target = new OhdpProjectPosition();
                target.setProjectId(copied.getId());
                target.setJobName(src.getJobName());
                target.setShiftSystem(src.getShiftSystem());
                target.setWorkingHours(src.getWorkingHours());
                target.setWorkerCount(src.getWorkerCount());
                target.setWorkLocation(src.getWorkLocation());
                target.setOperationType(src.getOperationType());
                target.setOperationContent(src.getOperationContent());
                target.setOperationDuration(src.getOperationDuration());
                target.setHazards(src.getHazards());
                target.setPpe(src.getPpe());
                target.setProtectionFacilities(src.getProtectionFacilities());
                target.setDeleted(false);
                copiedPositions.add(target);
            }
            positionRepository.saveAll(copiedPositions);
        }

        List<OhdpResultItem> sourceResults = resultItemRepository.findByProjectIdAndDeletedFalseOrderByIdAsc(sourceProjectId);
        if (!sourceResults.isEmpty()) {
            List<OhdpResultItem> copiedResults = new ArrayList<>();
            for (OhdpResultItem src : sourceResults) {
                OhdpResultItem target = new OhdpResultItem();
                target.setProjectId(copied.getId());
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
        List<OhdpProjectPosition> positions = positionRepository.findByProjectIdAndDeletedFalseOrderByIdAsc(projectId);
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

    private void applyProjectRequest(OhdpProject project, OhdpDto.ProjectRequest request, boolean createMode) {
        if (request == null) {
            throw new BusinessException("project request is required");
        }
        OhdpCompanyProfile profile = null;
        if (request.getCompanyProfileId() != null) {
            profile = companyProfileRepository.findByIdAndDeletedFalse(request.getCompanyProfileId())
                    .orElseThrow(() -> new BusinessException("company profile not found: " + request.getCompanyProfileId()));
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
                createMode ? DEFAULT_DETECT_TYPE : project.getDetectType(),
                DEFAULT_DETECT_TYPE);

        if (isBlank(companyName)) {
            throw new BusinessException("companyName is required");
        }
        if (isBlank(companyAddress)) {
            throw new BusinessException("companyAddress is required");
        }

        project.setCompanyName(companyName);
        project.setCompanyAddress(companyAddress);
        project.setDetectType(detectType);
        project.setSamplingDate(request.getSamplingDate() == null ? LocalDate.now() : request.getSamplingDate());
        project.setReportDate(request.getReportDate() == null ? LocalDate.now() : request.getReportDate());
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
        OhdpFactorTemplate template = null;
        if (request.getFactorTemplateId() != null) {
            template = factorTemplateRepository.findByIdAndDeletedFalse(request.getFactorTemplateId())
                    .orElseThrow(() -> new BusinessException("factor template not found: " + request.getFactorTemplateId()));
        }

        String jobName = firstNonBlank(trimNullable(request.getJobName()), createMode ? null : result.getJobName());
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

        BigDecimal limitValue = request.getLimitValue();
        if (limitValue == null && template != null) {
            limitValue = parseDecimal(template.getDefaultLimitValue());
        }
        if (limitValue == null && !createMode) {
            limitValue = result.getLimitValue();
        }

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
        try (InputStream templateStream = new ClassPathResource(REPORT_TEMPLATE_PATH).getInputStream();
             HWPFDocument document = new HWPFDocument(templateStream);
             OutputStream outputStream = Files.newOutputStream(filePath)) {
            Range range = document.getRange();
            applyTemplateReplacements(range, project, positions, results);
            document.write(outputStream);
        } catch (IOException e) {
            throw new BusinessException("failed to generate template report document");
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

    private void applyTemplateReplacements(
            Range range,
            OhdpProject project,
            List<OhdpProjectPosition> positions,
            List<OhdpResultItem> results) {
        Map<String, String> replacements = new LinkedHashMap<>();
        replacements.put(TEMPLATE_PROJECT_CODE, valueOf(project.getProjectCode()));
        replacements.put(TEMPLATE_COMPANY_A, valueOf(project.getCompanyName()));
        replacements.put(TEMPLATE_COMPANY_B, valueOf(project.getCompanyName()));
        replacements.put(TEMPLATE_COMPANY_C, valueOf(project.getCompanyName()));
        replacements.put(TEMPLATE_COMPANY_D, valueOf(project.getCompanyName()));
        replacements.put(TEMPLATE_COMPANY_ADDRESS, valueOf(project.getCompanyAddress()));
        replacements.put(TEMPLATE_DETECT_TYPE, valueOf(project.getDetectType()));
        replacements.put(TEMPLATE_SAMPLING_DATE, formatChineseDate(project.getSamplingDate()));
        replacements.put(TEMPLATE_REPORT_DATE, formatChineseDate(project.getReportDate()));
        replacements.put("{{POSITION_COUNT}}", String.valueOf(positions.size()));
        replacements.put("{{RESULT_COUNT}}", String.valueOf(results.size()));

        replacements.entrySet().stream()
                .sorted(Comparator.comparingInt((Map.Entry<String, String> e) -> e.getKey().length()).reversed())
                .forEach(entry -> {
                    String source = entry.getKey();
                    String target = valueOf(entry.getValue());
                    if (!source.equals(target)) {
                        range.replaceText(source, target);
                    }
                });
    }

    private String formatChineseDate(LocalDate date) {
        return date == null ? "-" : CHINESE_DATE_FORMAT.format(date);
    }

    private OhdpDto.ReportPrecheckResponse buildReportPrecheck(Long projectId, OhdpProject project) {
        List<String> missingItems = new ArrayList<>();
        if (isBlank(project.getCompanyName())) {
            missingItems.add("project.companyName");
        }
        if (isBlank(project.getCompanyAddress())) {
            missingItems.add("project.companyAddress");
        }
        if (isBlank(project.getDetectType())) {
            missingItems.add("project.detectType");
        }
        if (project.getSamplingDate() == null) {
            missingItems.add("project.samplingDate");
        }
        if (project.getReportDate() == null) {
            missingItems.add("project.reportDate");
        }

        List<OhdpProjectPosition> positions = positionRepository.findByProjectIdAndDeletedFalseOrderByIdAsc(projectId);
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
        return trimmed.isEmpty() ? null : trimmed;
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

    private OhdpDto.ProjectResponse toProjectResponse(OhdpProject project) {
        return OhdpDto.ProjectResponse.builder()
                .id(project.getId())
                .projectCode(project.getProjectCode())
                .companyName(project.getCompanyName())
                .companyAddress(project.getCompanyAddress())
                .detectType(project.getDetectType())
                .samplingDate(project.getSamplingDate())
                .reportDate(project.getReportDate())
                .notes(project.getNotes())
                .status(project.getStatus())
                .createdAt(project.getCreatedAt())
                .updatedAt(project.getUpdatedAt())
                .build();
    }

    private OhdpDto.PositionResponse toPositionResponse(OhdpProjectPosition position) {
        return OhdpDto.PositionResponse.builder()
                .id(position.getId())
                .projectId(position.getProjectId())
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
}
