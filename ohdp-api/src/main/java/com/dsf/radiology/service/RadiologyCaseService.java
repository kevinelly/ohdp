package com.dsf.radiology.service;

import com.dsf.auth.security.AuthContext;
import com.dsf.common.exception.BusinessException;
import com.dsf.radiology.dto.RadiologyDto;
import com.dsf.radiology.entity.RadiologyAcceptanceRule;
import com.dsf.radiology.entity.RadiologyCase;
import com.dsf.radiology.entity.RadiologyMeasurement;
import com.dsf.radiology.entity.RadiologyProtectionPoint;
import com.dsf.radiology.model.RadiologyCaseStatus;
import com.dsf.radiology.model.RadiologyFieldRole;
import com.dsf.radiology.model.RadiologyJudgement;
import com.dsf.radiology.model.RadiologyModality;
import com.dsf.radiology.repository.RadiologyAcceptanceRuleRepository;
import com.dsf.radiology.repository.RadiologyAttachmentRepository;
import com.dsf.radiology.repository.RadiologyCaseRepository;
import com.dsf.radiology.repository.RadiologyMeasurementRepository;
import com.dsf.radiology.repository.RadiologyProtectionPointRepository;
import com.dsf.radiology.repository.RadiologyReportRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class RadiologyCaseService {

    private final RadiologyCaseRepository caseRepository;
    private final RadiologyMeasurementRepository measurementRepository;
    private final RadiologyProtectionPointRepository protectionPointRepository;
    private final RadiologyAcceptanceRuleRepository ruleRepository;
    private final RadiologyAttachmentRepository attachmentRepository;
    private final RadiologyReportRepository reportRepository;
    private final RadiologyCatalogService catalogService;
    private final RadiologyCalculationEngine calculationEngine;
    private final RadiologyStandardEvaluator standardEvaluator;
    private final ObjectMapper objectMapper;

    @Transactional
    public RadiologyDto.CaseDetail createCase(RadiologyDto.CaseRequest request) {
        String code = request.getCaseCode().trim();
        if (caseRepository.existsByCaseCodeAndDeletedFalse(code)) {
            throw new BusinessException("检测案例编号已存在：" + code);
        }
        RadiologyCase entity = new RadiologyCase();
        applyCaseRequest(entity, request);
        entity.setCaseCode(code);
        entity.setRevisionNo(1);
        entity.setStatus(RadiologyCaseStatus.DRAFT);
        entity.setCalculationVersion(RadiologyCatalogService.CALCULATION_VERSION);
        if (!StringUtils.hasText(entity.getStandardBasis())) {
            entity.setStandardBasis(defaultBasis(entity.getModality()));
        }
        entity = caseRepository.save(entity);
        seedRpdDefaults(entity);
        return detail(entity);
    }

    @Transactional
    public RadiologyDto.CaseDetail updateCase(Long id, RadiologyDto.CaseRequest request) {
        RadiologyCase entity = requireCase(id);
        assertEditable(entity);
        String code = request.getCaseCode().trim();
        if (!entity.getCaseCode().equals(code) && caseRepository.existsByCaseCodeAndDeletedFalse(code)) {
            throw new BusinessException("检测案例编号已存在：" + code);
        }
        applyCaseRequest(entity, request);
        entity.setCaseCode(code);
        return detail(caseRepository.save(entity));
    }

    @Transactional(readOnly = true)
    public RadiologyDto.CaseDetail getCase(Long id) {
        return detail(requireCase(id));
    }

    @Transactional(readOnly = true)
    public Page<RadiologyDto.CaseSummary> listCases(
            RadiologyModality modality,
            RadiologyCaseStatus status,
            String keyword,
            int page,
            int size) {
        String value = keyword == null ? "" : keyword.trim();
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by(Sort.Direction.DESC, "updatedAt"));
        Page<RadiologyCase> result;
        if (modality != null && status != null) {
            result = caseRepository.findByDeletedFalseAndModalityAndStatusAndCaseCodeContainingIgnoreCase(modality, status, value, pageable);
        } else if (modality != null) {
            result = caseRepository.findByDeletedFalseAndModalityAndCaseCodeContainingIgnoreCase(modality, value, pageable);
        } else if (status != null) {
            result = caseRepository.findByDeletedFalseAndStatusAndCaseCodeContainingIgnoreCase(status, value, pageable);
        } else {
            result = caseRepository.findByDeletedFalseAndCaseCodeContainingIgnoreCase(value, pageable);
        }
        return result.map(this::summary);
    }

    @Transactional
    public void deleteCase(Long id) {
        RadiologyCase entity = requireCase(id);
        if (entity.getStatus() == RadiologyCaseStatus.APPROVED || entity.getStatus() == RadiologyCaseStatus.REPORTED) {
            throw new BusinessException("已批准或已出具报告的案例不可删除，请使用修订功能");
        }
        entity.setDeleted(true);
        caseRepository.save(entity);
    }

    @Transactional
    public RadiologyDto.CaseDetail saveMeasurements(Long caseId, RadiologyDto.MeasurementBatchRequest request) {
        RadiologyCase entity = requireCase(caseId);
        assertEditable(entity);
        for (RadiologyDto.MeasurementRequest value : request.getValues()) {
            upsertRawMeasurement(entity, value.getItemCode(), value.getFieldCode(), value.getValueNum(), value.getValueText(), "MANUAL");
        }
        if (entity.getStatus() == RadiologyCaseStatus.DRAFT) {
            entity.setStatus(RadiologyCaseStatus.DATA_ENTERED);
            caseRepository.save(entity);
        }
        return detail(entity);
    }

    @Transactional
    public RadiologyDto.CalculationResponse calculate(Long caseId) {
        RadiologyCase entity = requireCase(caseId);
        assertEditable(entity);
        List<RadiologyMeasurement> measurements = measurementRepository.findByCaseIdAndDeletedFalseOrderByItemCodeAscSortNoAsc(caseId);
        Map<String, Map<String, BigDecimal>> numbersByItem = new LinkedHashMap<>();
        Map<String, Map<String, String>> textsByItem = new LinkedHashMap<>();
        for (RadiologyMeasurement measurement : measurements) {
            if (measurement.getFieldRole() != RadiologyFieldRole.RAW) continue;
            if (measurement.getValueNum() != null) {
                numbersByItem.computeIfAbsent(measurement.getItemCode(), ignored -> new LinkedHashMap<>())
                        .put(measurement.getFieldCode(), measurement.getValueNum());
            }
            if (StringUtils.hasText(measurement.getValueText())) {
                textsByItem.computeIfAbsent(measurement.getItemCode(), ignored -> new LinkedHashMap<>())
                        .put(measurement.getFieldCode(), measurement.getValueText());
            }
        }

        List<RadiologyDto.ItemCalculationResponse> responses = new ArrayList<>();
        for (RadiologyCatalogService.ItemDefinition item : catalogService.getCatalog(entity.getModality())) {
            Map<String, BigDecimal> itemNumbers = numbersByItem.getOrDefault(item.code(), Map.of());
            Map<String, String> itemTexts = textsByItem.getOrDefault(item.code(), Map.of());
            boolean hasRpdBackground = itemNumbers.keySet().stream().anyMatch(code -> code.startsWith("RPD0-"));
            if (itemNumbers.isEmpty() && itemTexts.isEmpty()) continue;
            if (item.code().equals("RPD") && !hasRpdBackground) continue;
            RadiologyCalculationEngine.CalculationResult result = calculationEngine.calculate(
                    item.code(), itemNumbers, itemTexts, numbersByItem, textsByItem);
            markOldCalculatedFieldsDeleted(caseId, item.code());
            if (result.complete()) {
                int sort = 1000;
                for (RadiologyCalculationEngine.CalculatedField field : result.fields()) {
                    upsertCalculatedMeasurement(entity, item, field, sort++);
                }
            }
            RadiologyStandardEvaluator.Evaluation judgment = standardEvaluator.evaluate(
                    entity.getModality(), entity.getDetectionCategory(), item.code(), result.complete(),
                    result.resultValue(), result.resultText(), itemNumbers, result.fields());
            responses.add(new RadiologyDto.ItemCalculationResponse(
                    item.code(), item.name(), result.complete(), result.message(), result.resultValue(), result.resultText(),
                    result.resultUnit(), judgment.judgement(), judgment.description()));
        }

        calculateProtectionPoints(entity);
        long completed = responses.stream().filter(RadiologyDto.ItemCalculationResponse::complete).count();
        long incomplete = responses.size() - completed;
        if (completed > 0) {
            entity.setStatus(RadiologyCaseStatus.CALCULATED);
            entity.setCalculationVersion(RadiologyCatalogService.CALCULATION_VERSION);
            caseRepository.save(entity);
        }
        return new RadiologyDto.CalculationResponse(caseId, entity.getCalculationVersion(), (int) completed, (int) incomplete, responses);
    }

    @Transactional
    public List<RadiologyDto.ProtectionPointResponse> saveProtectionPoints(
            Long caseId, RadiologyDto.ProtectionPointBatchRequest request) {
        RadiologyCase entity = requireCase(caseId);
        assertEditable(entity);
        for (RadiologyDto.ProtectionPointRequest value : request.getPoints()) {
            RadiologyProtectionPoint point = protectionPointRepository
                    .findByCaseIdAndPointCodeAndDeletedFalse(caseId, value.getPointCode().trim())
                    .orElseGet(RadiologyProtectionPoint::new);
            point.setCaseId(caseId);
            point.setPointCode(value.getPointCode().trim());
            point.setPointName(value.getPointName().trim());
            point.setLocationDescription(value.getLocationDescription());
            point.setReadingsJson(writeJson(value.getReadings()));
            point.setAnnualWorkloadMas(nonNegative(value.getAnnualWorkloadMas(), "年工作负荷"));
            point.setOccupancyFactor(factor(value.getOccupancyFactor(), "居留因子", BigDecimal.ONE));
            point.setUseFactor(factor(value.getUseFactor(), "使用因子", BigDecimal.ONE));
            point.setSortNo(value.getSortNo() == null ? 0 : value.getSortNo());
            protectionPointRepository.save(point);
        }
        calculateProtectionPoints(entity);
        if (entity.getStatus() == RadiologyCaseStatus.DRAFT) {
            entity.setStatus(RadiologyCaseStatus.DATA_ENTERED);
            caseRepository.save(entity);
        }
        return pointResponses(caseId);
    }

    @Transactional
    public void deleteProtectionPoint(Long caseId, Long pointId) {
        RadiologyCase entity = requireCase(caseId);
        assertEditable(entity);
        RadiologyProtectionPoint point = protectionPointRepository.findById(pointId)
                .filter(value -> value.getCaseId().equals(caseId) && !Boolean.TRUE.equals(value.getDeleted()))
                .orElseThrow(() -> new BusinessException("防护检测点不存在"));
        point.setDeleted(true);
        protectionPointRepository.save(point);
    }

    @Transactional
    public RadiologyDto.CaseDetail submitForReview(Long caseId, RadiologyDto.WorkflowRequest request) {
        RadiologyCase entity = requireCase(caseId);
        requireStatus(entity, RadiologyCaseStatus.CALCULATED);
        long rawItems = measurementRepository.findByCaseIdAndDeletedFalseOrderByItemCodeAscSortNoAsc(caseId).stream()
                .filter(value -> value.getFieldRole() == RadiologyFieldRole.RAW)
                .filter(value -> value.getValueNum() != null || StringUtils.hasText(value.getValueText()))
                .filter(value -> !value.getItemCode().equals("RPD")
                        || (!value.getFieldCode().equals("k") && !value.getFieldCode().equals("a")
                        && !value.getFieldCode().equals("RPD-mA")))
                .map(RadiologyMeasurement::getItemCode).distinct().count();
        long resultItems = measurementRepository.findByCaseIdAndDeletedFalseOrderByItemCodeAscSortNoAsc(caseId).stream()
                .filter(value -> value.getFieldRole() == RadiologyFieldRole.FINAL)
                .map(RadiologyMeasurement::getItemCode).distinct().count();
        if (rawItems == 0 || resultItems < rawItems) {
            throw new BusinessException("仍有已录入项目未完成计算，请根据计算提示补齐数据");
        }
        entity.setStatus(RadiologyCaseStatus.PENDING_REVIEW);
        entity.setReviewComment(request.getComment());
        caseRepository.save(entity);
        return detail(entity);
    }

    @Transactional
    public RadiologyDto.CaseDetail review(Long caseId, RadiologyDto.WorkflowRequest request) {
        RadiologyCase entity = requireCase(caseId);
        requireStatus(entity, RadiologyCaseStatus.PENDING_REVIEW);
        entity.setReviewerName(operator(request.getOperatorName()));
        entity.setReviewComment(request.getComment());
        entity.setStatus(RadiologyCaseStatus.REVIEWED);
        caseRepository.save(entity);
        return detail(entity);
    }

    @Transactional
    public RadiologyDto.CaseDetail approve(Long caseId, RadiologyDto.WorkflowRequest request) {
        RadiologyCase entity = requireCase(caseId);
        requireStatus(entity, RadiologyCaseStatus.REVIEWED);
        entity.setApproverName(operator(request.getOperatorName()));
        entity.setApprovalComment(request.getComment());
        entity.setReportDate(entity.getReportDate() == null ? LocalDate.now() : entity.getReportDate());
        entity.setStatus(RadiologyCaseStatus.APPROVED);
        caseRepository.save(entity);
        return detail(entity);
    }

    @Transactional
    public RadiologyDto.CaseDetail reopenAsRevision(Long caseId) {
        RadiologyCase source = requireCase(caseId);
        if (source.getStatus() != RadiologyCaseStatus.APPROVED && source.getStatus() != RadiologyCaseStatus.REPORTED) {
            throw new BusinessException("仅已批准或已出具报告的案例可创建修订版");
        }
        RadiologyCase target = new RadiologyCase();
        copyCase(source, target);
        target.setId(null);
        target.setSourceCaseId(source.getId());
        target.setRevisionNo(source.getRevisionNo() + 1);
        target.setCaseCode(nextRevisionCode(source.getCaseCode(), target.getRevisionNo()));
        target.setStatus(RadiologyCaseStatus.DRAFT);
        target.setReviewComment(null);
        target.setApprovalComment(null);
        target = caseRepository.save(target);

        for (RadiologyMeasurement value : measurementRepository.findByCaseIdAndDeletedFalseOrderByItemCodeAscSortNoAsc(source.getId())) {
            if (value.getFieldRole() != RadiologyFieldRole.RAW) continue;
            RadiologyMeasurement copy = new RadiologyMeasurement();
            copy.setCaseId(target.getId());
            copy.setItemCode(value.getItemCode());
            copy.setFieldCode(value.getFieldCode());
            copy.setFieldLabel(value.getFieldLabel());
            copy.setFieldRole(value.getFieldRole());
            copy.setValueNum(value.getValueNum());
            copy.setValueText(value.getValueText());
            copy.setUnit(value.getUnit());
            copy.setSortNo(value.getSortNo());
            copy.setValueSource("REVISION_COPY");
            measurementRepository.save(copy);
        }
        for (RadiologyProtectionPoint value : protectionPointRepository.findByCaseIdAndDeletedFalseOrderBySortNoAsc(source.getId())) {
            RadiologyProtectionPoint copy = new RadiologyProtectionPoint();
            copy.setCaseId(target.getId());
            copy.setPointCode(value.getPointCode());
            copy.setPointName(value.getPointName());
            copy.setLocationDescription(value.getLocationDescription());
            copy.setReadingsJson(value.getReadingsJson());
            copy.setAnnualWorkloadMas(value.getAnnualWorkloadMas());
            copy.setOccupancyFactor(value.getOccupancyFactor());
            copy.setUseFactor(value.getUseFactor());
            copy.setSortNo(value.getSortNo());
            protectionPointRepository.save(copy);
        }
        return detail(target);
    }

    @Transactional(readOnly = true)
    public List<RadiologyDto.RuleResponse> listRules(RadiologyModality modality) {
        return ruleRepository.findByDeletedFalseAndModalityOrderByItemCodeAsc(modality).stream().map(this::ruleResponse).toList();
    }

    @Transactional
    public RadiologyDto.RuleResponse saveRule(Long id, RadiologyDto.RuleRequest request) {
        RadiologyCatalogService.ItemDefinition item = catalogService.findItem(request.getModality(), request.getItemCode())
                .orElseThrow(() -> new BusinessException("未知检测项目：" + request.getItemCode()));
        RadiologyAcceptanceRule rule = id == null ? new RadiologyAcceptanceRule() : ruleRepository.findById(id)
                .filter(value -> !Boolean.TRUE.equals(value.getDeleted()))
                .orElseThrow(() -> new BusinessException("判定规则不存在"));
        rule.setModality(request.getModality());
        rule.setItemCode(item.code());
        rule.setItemName(item.name());
        rule.setRuleVersion(request.getRuleVersion());
        rule.setLowerLimit(request.getLowerLimit());
        rule.setUpperLimit(request.getUpperLimit());
        rule.setExpectedText(request.getExpectedText());
        rule.setUnit(request.getUnit());
        rule.setStandardCode(request.getStandardCode());
        rule.setStandardName(request.getStandardName());
        rule.setEnabled(request.getEnabled() == null || request.getEnabled());
        return ruleResponse(ruleRepository.save(rule));
    }

    @Transactional
    public void deleteRule(Long id) {
        RadiologyAcceptanceRule rule = ruleRepository.findById(id)
                .filter(value -> !Boolean.TRUE.equals(value.getDeleted()))
                .orElseThrow(() -> new BusinessException("判定规则不存在"));
        rule.setDeleted(true);
        ruleRepository.save(rule);
    }

    public RadiologyCase requireCase(Long id) {
        return caseRepository.findByIdAndDeletedFalse(id).orElseThrow(() -> new BusinessException("检测案例不存在"));
    }

    public void assertEditable(RadiologyCase entity) {
        if (entity.getStatus() == RadiologyCaseStatus.PENDING_REVIEW
                || entity.getStatus() == RadiologyCaseStatus.REVIEWED
                || entity.getStatus() == RadiologyCaseStatus.APPROVED
                || entity.getStatus() == RadiologyCaseStatus.REPORTED) {
            throw new BusinessException("当前状态不可修改检测数据，请退回或创建修订版");
        }
    }

    void upsertRawMeasurement(
            RadiologyCase entity,
            String itemCode,
            String fieldCode,
            BigDecimal valueNum,
            String valueText,
            String source) {
        RadiologyCatalogService.ItemDefinition item = catalogService.findItem(entity.getModality(), itemCode)
                .orElseThrow(() -> new BusinessException("未知检测项目：" + itemCode));
        RadiologyCatalogService.FieldDefinition field = catalogService.findField(entity.getModality(), item.code(), fieldCode)
                .orElseThrow(() -> new BusinessException(item.code() + " 不包含字段：" + fieldCode));
        RadiologyMeasurement measurement = measurementRepository
                .findByCaseIdAndItemCodeAndFieldCode(entity.getId(), item.code(), field.code())
                .orElseGet(RadiologyMeasurement::new);
        measurement.setCaseId(entity.getId());
        measurement.setItemCode(item.code());
        measurement.setFieldCode(field.code());
        measurement.setFieldLabel(field.label());
        measurement.setFieldRole(RadiologyFieldRole.RAW);
        measurement.setValueNum(valueNum);
        measurement.setValueText(StringUtils.hasText(valueText) ? valueText.trim() : null);
        measurement.setUnit(field.unit());
        measurement.setSortNo(item.sortNo() * 100 + field.sortNo());
        measurement.setValueSource(source);
        measurement.setDeleted(false);
        measurementRepository.save(measurement);
    }

    private void upsertCalculatedMeasurement(
            RadiologyCase entity,
            RadiologyCatalogService.ItemDefinition item,
            RadiologyCalculationEngine.CalculatedField field,
            int sortNo) {
        RadiologyMeasurement measurement = measurementRepository
                .findByCaseIdAndItemCodeAndFieldCode(entity.getId(), item.code(), field.code())
                .orElseGet(RadiologyMeasurement::new);
        measurement.setCaseId(entity.getId());
        measurement.setItemCode(item.code());
        measurement.setFieldCode(field.code());
        measurement.setFieldLabel(field.label());
        measurement.setFieldRole(field.code().equals("RESULT") || field.code().equals("RESULT_TEXT")
                ? RadiologyFieldRole.FINAL : RadiologyFieldRole.INTERMEDIATE);
        measurement.setValueNum(field.valueNum());
        measurement.setValueText(field.valueText());
        measurement.setUnit(field.unit());
        measurement.setSortNo(item.sortNo() * 100 + sortNo);
        measurement.setValueSource("CALCULATED");
        measurement.setDeleted(false);
        measurementRepository.save(measurement);
    }

    private void markOldCalculatedFieldsDeleted(Long caseId, String itemCode) {
        for (RadiologyMeasurement measurement : measurementRepository
                .findByCaseIdAndItemCodeAndDeletedFalseOrderBySortNoAsc(caseId, itemCode)) {
            if (measurement.getFieldRole() != RadiologyFieldRole.RAW) {
                measurement.setDeleted(true);
                measurementRepository.save(measurement);
            }
        }
    }

    private void calculateProtectionPoints(RadiologyCase entity) {
        Map<String, RadiologyMeasurement> rpd = measurementRepository
                .findByCaseIdAndItemCodeAndDeletedFalseOrderBySortNoAsc(entity.getId(), "RPD").stream()
                .filter(value -> value.getFieldRole() == RadiologyFieldRole.RAW)
                .collect(Collectors.toMap(RadiologyMeasurement::getFieldCode, value -> value, (left, right) -> right));
        BigDecimal k = Optional.ofNullable(rpd.get("k")).map(RadiologyMeasurement::getValueNum).orElse(BigDecimal.ONE);
        BigDecimal a = Optional.ofNullable(rpd.get("a")).map(RadiologyMeasurement::getValueNum).orElse(new BigDecimal("0.001"));
        BigDecimal limit = entity.getModality() == RadiologyModality.DR ? new BigDecimal("25") : new BigDecimal("2.5");
        BigDecimal normalizationFactor = BigDecimal.ONE;
        if (entity.getModality() == RadiologyModality.DR) {
            BigDecimal testCurrent = Optional.ofNullable(rpd.get("RPD-mA"))
                    .map(RadiologyMeasurement::getValueNum).orElse(new BigDecimal("100"));
            if (testCurrent.signum() <= 0) throw new BusinessException("DR 防护检测管电流必须大于 0 mA");
            normalizationFactor = new BigDecimal("100").divide(testCurrent, 12, java.math.RoundingMode.HALF_UP);
        }
        for (RadiologyProtectionPoint point : protectionPointRepository.findByCaseIdAndDeletedFalseOrderBySortNoAsc(entity.getId())) {
            List<BigDecimal> calculated = calculationEngine.calculateProtectionReadings(
                    readReadings(point.getReadingsJson()), k, a, normalizationFactor);
            if (calculated.isEmpty()) {
                point.setAverageValue(null);
                point.setResultValue(null);
                point.setJudgement(RadiologyJudgement.INCOMPLETE);
            } else {
                point.setAverageValue(calculated.get(0));
                point.setResultValue(calculated.get(1));
                point.setControlLimit(limit);
                if (entity.getModality() == RadiologyModality.DR) {
                    point.setAnnualDoseLimit(new BigDecimal("0.25"));
                    point.setAnnualEffectiveDose(calculateAnnualEffectiveDose(point));
                    if (point.getResultValue().compareTo(limit) <= 0) {
                        point.setJudgement(RadiologyJudgement.QUALIFIED);
                    } else if (point.getAnnualEffectiveDose() == null) {
                        point.setJudgement(RadiologyJudgement.NEEDS_ASSESSMENT);
                    } else {
                        point.setJudgement(point.getAnnualEffectiveDose().compareTo(point.getAnnualDoseLimit()) <= 0
                                ? RadiologyJudgement.QUALIFIED : RadiologyJudgement.UNQUALIFIED);
                    }
                } else {
                    point.setAnnualEffectiveDose(null);
                    point.setAnnualDoseLimit(null);
                    point.setJudgement(point.getResultValue().compareTo(limit) <= 0
                            ? RadiologyJudgement.QUALIFIED : RadiologyJudgement.UNQUALIFIED);
                }
            }
            protectionPointRepository.save(point);
        }
    }

    private JudgmentResult judge(
            RadiologyModality modality, String itemCode, BigDecimal result, String resultText, boolean complete) {
        if (!complete) return new JudgmentResult(RadiologyJudgement.INCOMPLETE, "数据不完整");
        Optional<RadiologyAcceptanceRule> optional = ruleRepository
                .findFirstByDeletedFalseAndEnabledTrueAndModalityAndItemCodeOrderByRuleVersionDesc(modality, itemCode);
        if (optional.isEmpty()) return new JudgmentResult(RadiologyJudgement.NOT_CONFIGURED, "未配置判定限值，仅完成计算");
        RadiologyAcceptanceRule rule = optional.get();
        if (result == null && (rule.getLowerLimit() != null || rule.getUpperLimit() != null)) {
            return new JudgmentResult(
                    RadiologyJudgement.NOT_CONFIGURED,
                    "该项目包含不同单位的分组结果，不能使用单一数值限值，请按计算明细人工复核");
        }
        boolean qualified = true;
        if (rule.getLowerLimit() != null) qualified = result != null && result.compareTo(rule.getLowerLimit()) >= 0;
        if (qualified && rule.getUpperLimit() != null) qualified = result != null && result.compareTo(rule.getUpperLimit()) <= 0;
        if (qualified && StringUtils.hasText(rule.getExpectedText())) {
            qualified = resultText != null && resultText.trim().equalsIgnoreCase(rule.getExpectedText().trim());
        }
        return new JudgmentResult(
                qualified ? RadiologyJudgement.QUALIFIED : RadiologyJudgement.UNQUALIFIED,
                rule.getStandardCode() + " / " + rule.getRuleVersion() + " / " + describeLimits(rule));
    }

    private String describeLimits(RadiologyAcceptanceRule rule) {
        List<String> parts = new ArrayList<>();
        if (rule.getLowerLimit() != null) parts.add("≥" + rule.getLowerLimit());
        if (rule.getUpperLimit() != null) parts.add("≤" + rule.getUpperLimit());
        if (StringUtils.hasText(rule.getExpectedText())) parts.add("应为“" + rule.getExpectedText() + "”");
        return String.join("，", parts) + (StringUtils.hasText(rule.getUnit()) ? " " + rule.getUnit() : "");
    }

    private void seedRpdDefaults(RadiologyCase entity) {
        upsertRawMeasurement(entity, "RPD", "k", BigDecimal.ONE, null, "DEFAULT");
        upsertRawMeasurement(entity, "RPD", "a", new BigDecimal("0.001"), null, "DEFAULT");
        if (entity.getModality() == RadiologyModality.DR) {
            upsertRawMeasurement(entity, "RPD", "RPD-mA", new BigDecimal("100"), null, "DEFAULT");
        }
    }

    private void applyCaseRequest(RadiologyCase entity, RadiologyDto.CaseRequest request) {
        entity.setModality(request.getModality());
        entity.setSampleAcceptanceNo(request.getSampleAcceptanceNo());
        entity.setInspectedUnitName(request.getInspectedUnitName().trim());
        entity.setInspectedUnitAddress(request.getInspectedUnitAddress());
        entity.setContactName(request.getContactName());
        entity.setContactPhone(request.getContactPhone());
        entity.setEquipmentName(request.getEquipmentName().trim());
        entity.setEquipmentModel(request.getEquipmentModel());
        entity.setEquipmentNo(request.getEquipmentNo());
        entity.setManufacturer(request.getManufacturer());
        entity.setPhantomName(request.getPhantomName());
        entity.setPhantomManufacturer(request.getPhantomManufacturer());
        entity.setPhantomModel(request.getPhantomModel());
        entity.setPhantomSerialNo(request.getPhantomSerialNo());
        entity.setPhantomManualReference(request.getPhantomManualReference());
        entity.setDetectionCategory(request.getDetectionCategory());
        entity.setDetectionLocation(request.getDetectionLocation());
        entity.setInspectionDate(request.getInspectionDate());
        entity.setReportDate(request.getReportDate());
        entity.setStandardBasis(request.getStandardBasis());
        entity.setInstrumentDescription(request.getInstrumentDescription());
        entity.setWriterName(request.getWriterName());
        entity.setReviewerName(request.getReviewerName());
        entity.setApproverName(request.getApproverName());
        entity.setNotes(request.getNotes());
    }

    private void copyCase(RadiologyCase source, RadiologyCase target) {
        target.setModality(source.getModality());
        target.setSampleAcceptanceNo(source.getSampleAcceptanceNo());
        target.setInspectedUnitName(source.getInspectedUnitName());
        target.setInspectedUnitAddress(source.getInspectedUnitAddress());
        target.setContactName(source.getContactName());
        target.setContactPhone(source.getContactPhone());
        target.setEquipmentName(source.getEquipmentName());
        target.setEquipmentModel(source.getEquipmentModel());
        target.setEquipmentNo(source.getEquipmentNo());
        target.setManufacturer(source.getManufacturer());
        target.setPhantomName(source.getPhantomName());
        target.setPhantomManufacturer(source.getPhantomManufacturer());
        target.setPhantomModel(source.getPhantomModel());
        target.setPhantomSerialNo(source.getPhantomSerialNo());
        target.setPhantomManualReference(source.getPhantomManualReference());
        target.setDetectionCategory(source.getDetectionCategory());
        target.setDetectionLocation(source.getDetectionLocation());
        target.setInspectionDate(source.getInspectionDate());
        target.setReportDate(source.getReportDate());
        target.setStandardBasis(source.getStandardBasis());
        target.setInstrumentDescription(source.getInstrumentDescription());
        target.setWriterName(source.getWriterName());
        target.setReviewerName(source.getReviewerName());
        target.setApproverName(source.getApproverName());
        target.setNotes(source.getNotes());
        target.setCalculationVersion(source.getCalculationVersion());
    }

    private RadiologyDto.CaseSummary summary(RadiologyCase entity) {
        return new RadiologyDto.CaseSummary(
                entity.getId(), entity.getCaseCode(), entity.getModality(), entity.getRevisionNo(), entity.getInspectedUnitName(),
                entity.getEquipmentName(), entity.getEquipmentModel(), entity.getInspectionDate(), entity.getStatus(),
                entity.getCreatedAt(), entity.getUpdatedAt());
    }

    private RadiologyDto.CaseDetail detail(RadiologyCase entity) {
        List<RadiologyDto.MeasurementResponse> measurements = measurementRepository
                .findByCaseIdAndDeletedFalseOrderByItemCodeAscSortNoAsc(entity.getId()).stream().map(this::measurementResponse).toList();
        return new RadiologyDto.CaseDetail(
                entity.getId(), entity.getCaseCode(), entity.getModality(), entity.getRevisionNo(), entity.getSourceCaseId(),
                entity.getSampleAcceptanceNo(), entity.getInspectedUnitName(), entity.getInspectedUnitAddress(), entity.getContactName(),
                entity.getContactPhone(), entity.getEquipmentName(), entity.getEquipmentModel(), entity.getEquipmentNo(), entity.getManufacturer(),
                entity.getPhantomName(), entity.getPhantomManufacturer(), entity.getPhantomModel(), entity.getPhantomSerialNo(), entity.getPhantomManualReference(),
                entity.getDetectionCategory(), entity.getDetectionLocation(), entity.getInspectionDate(), entity.getReportDate(),
                entity.getStandardBasis(), entity.getInstrumentDescription(), entity.getWriterName(), entity.getReviewerName(), entity.getApproverName(),
                entity.getReviewComment(), entity.getApprovalComment(), entity.getNotes(), entity.getCalculationVersion(), entity.getStatus(),
                measurements, pointResponses(entity.getId()),
                attachmentRepository.findByCaseIdAndDeletedFalseOrderByUploadedAtDesc(entity.getId()).stream()
                        .map(RadiologyAttachmentService::toResponse).toList(),
                reportRepository.findByCaseIdAndDeletedFalseOrderByGeneratedAtDesc(entity.getId()).stream()
                        .map(RadiologyReportService::toResponse).toList(),
                entity.getCreatedAt(), entity.getUpdatedAt());
    }

    private RadiologyDto.MeasurementResponse measurementResponse(RadiologyMeasurement value) {
        return new RadiologyDto.MeasurementResponse(
                value.getId(), value.getItemCode(), value.getFieldCode(), value.getFieldLabel(), value.getFieldRole(),
                value.getValueNum(), value.getValueText(), value.getUnit(), value.getSortNo(), value.getValueSource());
    }

    private List<RadiologyDto.ProtectionPointResponse> pointResponses(Long caseId) {
        return protectionPointRepository.findByCaseIdAndDeletedFalseOrderBySortNoAsc(caseId).stream()
                .map(value -> new RadiologyDto.ProtectionPointResponse(
                        value.getId(), value.getPointCode(), value.getPointName(), value.getLocationDescription(),
                        readReadings(value.getReadingsJson()), value.getAverageValue(), value.getResultValue(), value.getControlLimit(),
                        value.getAnnualWorkloadMas(), value.getOccupancyFactor(), value.getUseFactor(),
                        value.getAnnualEffectiveDose(), value.getAnnualDoseLimit(),
                        value.getJudgement(), value.getSortNo()))
                .toList();
    }

    private BigDecimal calculateAnnualEffectiveDose(RadiologyProtectionPoint point) {
        if (point.getAnnualWorkloadMas() == null || point.getAnnualWorkloadMas().signum() <= 0
                || point.getResultValue() == null) return null;
        BigDecimal occupancy = point.getOccupancyFactor() == null ? BigDecimal.ONE : point.getOccupancyFactor();
        BigDecimal use = point.getUseFactor() == null ? BigDecimal.ONE : point.getUseFactor();
        // Result is normalized to a 100 mA test condition. Workload/(100*3600) converts annual mAs to equivalent hours.
        return point.getResultValue()
                .multiply(point.getAnnualWorkloadMas())
                .multiply(occupancy)
                .multiply(use)
                .divide(new BigDecimal("360000000"), 8, java.math.RoundingMode.HALF_UP);
    }

    private BigDecimal nonNegative(BigDecimal value, String label) {
        if (value != null && value.signum() < 0) throw new BusinessException(label + "不能小于 0");
        return value;
    }

    private BigDecimal factor(BigDecimal value, String label, BigDecimal defaultValue) {
        BigDecimal actual = value == null ? defaultValue : value;
        if (actual.signum() <= 0 || actual.compareTo(BigDecimal.ONE) > 0) {
            throw new BusinessException(label + "必须大于 0 且不超过 1");
        }
        return actual;
    }

    private RadiologyDto.RuleResponse ruleResponse(RadiologyAcceptanceRule value) {
        return new RadiologyDto.RuleResponse(
                value.getId(), value.getModality(), value.getItemCode(), value.getItemName(), value.getRuleVersion(),
                value.getLowerLimit(), value.getUpperLimit(), value.getExpectedText(), value.getUnit(), value.getStandardCode(),
                value.getStandardName(), value.getEnabled());
    }

    private List<BigDecimal> readReadings(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<BigDecimal>>() {});
        } catch (JsonProcessingException ex) {
            throw new BusinessException("防护点读数格式损坏");
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new BusinessException("数据序列化失败");
        }
    }

    private void requireStatus(RadiologyCase entity, RadiologyCaseStatus expected) {
        if (entity.getStatus() != expected) {
            throw new BusinessException("当前状态为 " + entity.getStatus() + "，要求状态为 " + expected);
        }
    }

    private String operator(String requested) {
        if (StringUtils.hasText(requested)) return requested.trim();
        return AuthContext.get() == null ? "system" : AuthContext.get().getUsername();
    }

    private String defaultBasis(RadiologyModality modality) {
        return modality == RadiologyModality.DR
                ? "WS 76—2020《医用常规X射线诊断设备质量控制检测规范》；GBZ 130—2020《放射诊断放射防护要求》"
                : "WS 519—2019《X射线计算机体层摄影装置质量控制检测规范》；GBZ 130—2020《放射诊断放射防护要求》";
    }

    private String nextRevisionCode(String sourceCode, int revision) {
        String base = sourceCode.replaceFirst("-R\\d+$", "");
        String candidate = base + "-R" + revision;
        int suffix = revision;
        while (caseRepository.existsByCaseCodeAndDeletedFalse(candidate)) {
            candidate = base + "-R" + (++suffix);
        }
        return candidate;
    }

    private record JudgmentResult(RadiologyJudgement judgement, String description) {
    }
}
