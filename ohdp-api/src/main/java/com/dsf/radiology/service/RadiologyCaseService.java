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
    private final RadiologyScopeService scopeService;
    private final com.dsf.auth.service.rbac.RbacAccessService accessService;
    private final ObjectMapper objectMapper;
    private final com.dsf.radiology.config.RadiologyStorageProperties storageProperties;

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
        checkVersion(entity, request.getExpectedDataVersion());
        if (entity.getModality()!=request.getModality()) throw new BusinessException("设备类别创建后不可更改，请新建案例");
        applyCaseRequest(entity, request);
        entity.setCaseCode(code);
        invalidate(entity);
        return detail(caseRepository.saveAndFlush(entity));
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
        return searchCases(modality,null,status,keyword,null,null,null,null,null,null,null,page,size);
    }

    @Transactional(readOnly=true)
    public Page<RadiologyDto.CaseSummary> searchCases(RadiologyModality modality,List<RadiologyModality> modalities,
            RadiologyCaseStatus status,String keyword,Integer year,LocalDate from,LocalDate to,String city,String county,
            String receipt,String company,int page,int size) {
        if(from!=null && to!=null && to.isBefore(from)) throw new BusinessException("查询结束日期不能早于开始日期");
        if(year!=null && (year<1900 || year>2200)) throw new BusinessException("年份超出支持范围");
        return caseRepository.findAll((root,q,cb)->{
            var predicates=new ArrayList<jakarta.persistence.criteria.Predicate>();
            predicates.add(cb.isFalse(root.get("deleted")));
            if(modality!=null) predicates.add(cb.equal(root.get("modality"),modality));
            if(modalities!=null && !modalities.isEmpty()) predicates.add(root.get("modality").in(modalities));
            if(status!=null) predicates.add(cb.equal(root.get("status"),status));
            if(StringUtils.hasText(keyword)) {
                String like="%"+keyword.trim().toLowerCase(java.util.Locale.ROOT)+"%";
                predicates.add(cb.or(cb.like(cb.lower(root.get("caseCode")),like),cb.like(cb.lower(root.get("sampleAcceptanceNo")),like),
                    cb.like(cb.lower(root.get("inspectedUnitName")),like),cb.like(cb.lower(root.get("equipmentNo")),like),cb.like(cb.lower(root.get("region")),like)));
            }
            for(String field:List.of("city","county","sampleAcceptanceNo","inspectedUnitName")) {
                String value=switch(field){case "city"->city;case "county"->county;case "sampleAcceptanceNo"->receipt;default->company;};
                if(StringUtils.hasText(value)) predicates.add(cb.like(root.get(field),"%"+value.trim()+"%"));
            }
            LocalDate lower=from,upper=to;
            if(year!=null) {LocalDate ys=LocalDate.of(year,1,1),ye=LocalDate.of(year,12,31);
                lower=lower==null || lower.isBefore(ys) ? ys:lower;upper=upper==null || upper.isAfter(ye) ? ye:upper;}
            if(lower!=null) predicates.add(cb.greaterThanOrEqualTo(cb.<LocalDate>coalesce(root.get("inspectionEndDate"),root.get("inspectionDate")),lower));
            if(upper!=null) predicates.add(cb.lessThanOrEqualTo(root.get("inspectionDate"),upper));
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        },PageRequest.of(Math.max(page,0),Math.min(Math.max(size,1),100),Sort.by(Sort.Direction.DESC,"updatedAt"))).map(this::summary);
    }

    @Transactional
    public void deleteCase(Long id) {
        RadiologyCase entity = requireCase(id);
        if (entity.getStatus() == RadiologyCaseStatus.APPROVED || entity.getStatus() == RadiologyCaseStatus.REPORTED) {
            throw new BusinessException("已批准或已出具报告的案例不可删除，请使用修订功能");
        }
        assertEditable(entity);
        entity.setDeleted(true);
        caseRepository.save(entity);
    }

    @Transactional
    public RadiologyDto.CaseDetail saveMeasurements(Long caseId, RadiologyDto.MeasurementBatchRequest request) {
        RadiologyCase entity = requireCase(caseId);
        assertEditable(entity);
        checkVersion(entity, request.getExpectedDataVersion());
        if (request.getConditions()!=null) entity.setItemConditionsJson(writeJson(request.getConditions()));
        for (RadiologyDto.MeasurementRequest value : request.getValues()) {
            upsertRawMeasurement(entity, value.getItemCode(), value.getFieldCode(), value.getValueNum(), value.getValueText(), "MANUAL");
        }
        saveAicExposurePairs(entity);
        invalidate(entity);
        caseRepository.saveAndFlush(entity);
        return detail(entity);
    }

    private void saveAicExposurePairs(RadiologyCase entity) {
        Map<String,String> conditions=itemConditions(entity);
        try {
            if(!"mA,s".equals(objectMapper.readTree(conditions.getOrDefault("__params.AIC","{}")).path("doseUnit").asText())) return;
            var pairs=objectMapper.readTree(conditions.getOrDefault("__aic.pairs","{}"));
            for(String direction:List.of("C","L","R")) for(int i=1;i<=3;i++) {
                String field="AIC-"+direction+i;
                String current=pairs.path(field).path("current").asText("").trim(),time=pairs.path(field).path("time").asText("").trim();
                BigDecimal mas=null;
                if(!current.isEmpty() && !time.isEmpty()) {
                    BigDecimal amps=new BigDecimal(current),seconds=new BigDecimal(time);
                    if(amps.signum()<=0 || seconds.signum()<=0) throw new BusinessException("AEC电离室的电流和曝光时间必须大于0");
                    mas=amps.multiply(seconds);
                }
                upsertRawMeasurement(entity,"AIC",field,mas,null,"EXPOSURE_PAIR");
            }
        } catch(JsonProcessingException | NumberFormatException ex) {throw new BusinessException("AEC电离室电流/时间输入格式无效");}
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
                        .put(measurement.getFieldCode(), StringUtils.hasText(measurement.getValueText()) ? new BigDecimal(measurement.getValueText()) : measurement.getValueNum());
            }
            if (measurement.getValueNum()==null && StringUtils.hasText(measurement.getValueText())) {
                textsByItem.computeIfAbsent(measurement.getItemCode(), ignored -> new LinkedHashMap<>())
                        .put(measurement.getFieldCode(), measurement.getValueText());
            }
        }

        List<RadiologyDto.ItemCalculationResponse> responses = new ArrayList<>();
        for (RadiologyCatalogService.ItemDefinition item : catalogService.getCatalog(entity.getModality())) {
            Map<String, BigDecimal> itemNumbers = numbersByItem.getOrDefault(item.code(), Map.of());
            Map<String, String> itemTexts = textsByItem.getOrDefault(item.code(), Map.of());
            boolean hasRpdBackground = itemNumbers.keySet().stream().anyMatch(code -> code.startsWith("RPD0-"));
            String exclusion=scopeService.exclusion(entity, item.code(), itemNumbers);
            if(!groupSelected(entity,item.code())) exclusion="本次委托检测范围未包含此项目组";
            if (!exclusion.isEmpty()) {
                markOldCalculatedFieldsDeleted(caseId,item.code());
                responses.add(new RadiologyDto.ItemCalculationResponse(item.code(),item.name(),true,exclusion,null,"不检测","",
                        RadiologyJudgement.NOT_APPLICABLE,exclusion));
                continue;
            }
            itemNumbers=new LinkedHashMap<>(itemNumbers);
            if (item.code().equals("CTDI") && !RadiologyScopeService.type(entity.getDetectionCategory()).equals("ACCEPTANCE"))
                itemNumbers.put("__HEAD_ONLY",BigDecimal.ONE);
            if (item.code().equals("LCR") && RadiologyScopeService.type(entity.getDetectionCategory()).equals("ACCEPTANCE"))
                itemNumbers.put("__ESTABLISH_BASELINE",BigDecimal.ONE);
            if(item.code().equals("RPD") && StringUtils.hasText(entity.getProfileJson()) && profile(entity).getCalibrationMode()!=null && List.of("NONE","TIME").contains(profile(entity).getCalibrationMode())) itemNumbers.put("k",BigDecimal.ONE);
            if (item.code().equals("RPD")) itemNumbers.put("a",switch(entity.getRpdUnit()==null ? "nSv/h":entity.getRpdUnit()) {
                case "μSv/h" -> BigDecimal.ONE; case "mSv/h" -> new BigDecimal("1000"); default -> new BigDecimal("0.001");
            });
            String unitCondition=itemConditions(entity).get("__params."+item.code());
            if(item.code().equals("GTA") && StringUtils.hasText(itemConditions(entity).get("__method.GTA"))) {
                itemTexts=new LinkedHashMap<>(itemTexts);itemTexts.put("__METHOD",itemConditions(entity).get("__method.GTA"));
            }
            if(unitCondition!=null) try {String unit=objectMapper.readTree(unitCondition).path("doseUnit").asText();if(unit.equals("μGy"))itemNumbers.put("__MICROGY",BigDecimal.ONE);} catch(JsonProcessingException ex){throw new BusinessException("检测条件结构损坏");}
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
            var custom=ruleRepository.findByDeletedFalseAndModalityOrderByItemCodeAsc(entity.getModality()).stream()
                    .filter(r -> r.getItemCode().equals(item.code()) && Boolean.TRUE.equals(r.getEnabled()) && !r.getRuleVersion().startsWith("WS-"))
                    .filter(r -> r.getDetectionCategory()==null || r.getDetectionCategory().equals(entity.getDetectionCategory()))
                    .max(java.util.Comparator.comparing(RadiologyAcceptanceRule::getUpdatedAt));
            if (result.complete() && custom.isPresent() && !item.code().equals("RPD")) {
                var rule=custom.get();
                if (result.resultValue()==null && (rule.getLowerLimit()!=null || rule.getUpperLimit()!=null))
                    judgment=new RadiologyStandardEvaluator.Evaluation(RadiologyJudgement.NOT_CONFIGURED,"分组项目不能使用单一限值规则");
                else if (StringUtils.hasText(rule.getUnit()) && !rule.getUnit().equals(result.resultUnit()))
                    judgment=new RadiologyStandardEvaluator.Evaluation(RadiologyJudgement.NOT_CONFIGURED,"规则单位与计算结果单位不一致");
                else {
                    boolean ok=true;
                    if (rule.getLowerLimit()!=null) ok=result.resultValue()!=null && (Boolean.FALSE.equals(rule.getLowerInclusive()) ?
                            result.resultValue().compareTo(rule.getLowerLimit())>0 : result.resultValue().compareTo(rule.getLowerLimit())>=0);
                    if (rule.getUpperLimit()!=null) ok=ok && result.resultValue()!=null && (Boolean.FALSE.equals(rule.getUpperInclusive()) ?
                            result.resultValue().compareTo(rule.getUpperLimit())<0 : result.resultValue().compareTo(rule.getUpperLimit())<=0);
                    if (StringUtils.hasText(rule.getExpectedText())) ok=ok && rule.getExpectedText().equals(result.resultText());
                    judgment=new RadiologyStandardEvaluator.Evaluation(ok ? RadiologyJudgement.QUALIFIED : RadiologyJudgement.UNQUALIFIED,
                            rule.getStandardCode()+" / "+rule.getRuleVersion()+" / "+describeLimits(rule));
                }
            }
            responses.add(new RadiologyDto.ItemCalculationResponse(
                    item.code(), item.name(), result.complete(), result.message(), result.resultValue(), result.resultText(),
                    result.resultUnit(), judgment.judgement(), judgment.description()));
        }

        calculateProtectionPoints(entity);
        long completed = responses.stream().filter(RadiologyDto.ItemCalculationResponse::complete).count();
        long incomplete = responses.size() - completed;
        entity.setStatus(RadiologyCaseStatus.CALCULATED);
        entity.setCalculationVersion(RadiologyCatalogService.CALCULATION_VERSION);
        entity.setCalculatedDataVersion(dataVersion(entity));
        entity.setCalculationJson(writeJson(responses));
        entity.setRuleFingerprint(ruleFingerprint(entity));
        caseRepository.saveAndFlush(entity);
        return new RadiologyDto.CalculationResponse(caseId, entity.getCalculationVersion(), (int) completed, (int) incomplete, responses);
    }

    @Transactional
    public List<RadiologyDto.ProtectionPointResponse> saveProtectionPoints(
            Long caseId, RadiologyDto.ProtectionPointBatchRequest request) {
        RadiologyCase entity = requireCase(caseId);
        assertEditable(entity);
        checkVersion(entity, request.getExpectedDataVersion());
        for (RadiologyDto.ProtectionPointRequest value : request.getPoints()) {
            if (value.getReadings().size()!=5 || value.getReadings().stream().anyMatch(v -> v==null || v.signum()<0)) throw new BusinessException("每个防护点必须有5个非负原始读数");
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
        invalidate(entity);
        caseRepository.saveAndFlush(entity);
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
        invalidate(entity);
    }

    @Transactional
    public RadiologyDto.CaseDetail submitForReview(Long caseId, RadiologyDto.WorkflowRequest request) {
        RadiologyCase entity = requireCase(caseId);
        requireStatus(entity, RadiologyCaseStatus.CALCULATED);
        checkVersion(entity,request.getExpectedDataVersion());
        List<String> issues=submissionIssues(entity);
        if (!issues.isEmpty()) throw new BusinessException("无法提交审核："+String.join("；",issues));
        entity.setWriterName(operator(null));
        workflowEvent(entity,"SUBMIT",request.getComment());
        entity.setStatus(RadiologyCaseStatus.PENDING_REVIEW);
        entity.setReviewComment(request.getComment());
        caseRepository.save(entity);
        return detail(entity);
    }

    @Transactional
    public RadiologyDto.CaseDetail review(Long caseId, RadiologyDto.WorkflowRequest request) {
        RadiologyCase entity = requireCase(caseId);
        requireStatus(entity, RadiologyCaseStatus.PENDING_REVIEW);
        requirePermission("RADIOLOGY:WORKFLOW:REVIEW");
        checkVersion(entity,request.getExpectedDataVersion());
        entity.setReviewerName(operator(null));
        workflowEvent(entity,"REVIEW",request.getComment());
        entity.setReviewComment(request.getComment());
        entity.setStatus(RadiologyCaseStatus.REVIEWED);
        caseRepository.save(entity);
        return detail(entity);
    }

    @Transactional
    public RadiologyDto.CaseDetail approve(Long caseId, RadiologyDto.WorkflowRequest request) {
        RadiologyCase entity = requireCase(caseId);
        requireStatus(entity, RadiologyCaseStatus.REVIEWED);
        requirePermission("RADIOLOGY:WORKFLOW:APPROVE");
        checkVersion(entity,request.getExpectedDataVersion());
        if (!submissionIssues(entity).isEmpty()) throw new BusinessException("计算或规则已变更，请退回重新计算");
        entity.setApproverName(operator(null));
        workflowEvent(entity,"APPROVE",request.getComment());
        entity.setApprovalComment(request.getComment());
        entity.setReportDate(entity.getReportDate() == null ? LocalDate.now() : entity.getReportDate());
        entity.setStatus(RadiologyCaseStatus.APPROVED);
        caseRepository.saveAndFlush(entity);
        var approved=new ApprovedSnapshot(entity,
                measurementRepository.findByCaseIdAndDeletedFalseOrderByItemCodeAscSortNoAsc(caseId),
                protectionPointRepository.findByCaseIdAndDeletedFalseOrderBySortNoAsc(caseId),
                attachmentRepository.findByCaseIdAndDeletedFalseOrderByUploadedAtDesc(caseId),
                itemResults(entity),"2026.10.13",RadiologyReportService.templateHash(entity.getModality()));
        entity.setApprovedSnapshotJson(writeJson(approved));
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
        target.setRevisionNo(Integer.parseInt(target.getCaseCode().substring(target.getCaseCode().lastIndexOf("-R")+2)));
        target.setStatus(RadiologyCaseStatus.DRAFT);
        target.setReviewComment(null);
        target.setApprovalComment(null);
        target.setReviewerName(null);
        target.setApproverName(null);
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
        java.nio.file.Path root=java.nio.file.Path.of(storageProperties.getBaseDir()).toAbsolutePath().normalize();
        for (var value:attachmentRepository.findByCaseIdAndDeletedFalseOrderByUploadedAtDesc(source.getId())) {
            var from=java.nio.file.Path.of(value.getStoragePath()).toAbsolutePath().normalize();
            if (!from.startsWith(root) || !java.nio.file.Files.isRegularFile(from))
                throw new BusinessException("原版附件缺失，无法完整创建修订版："+value.getOriginalName());
            String name=java.util.UUID.randomUUID()+"-"+value.getStoredName();
            var to=root.resolve(target.getId().toString()).resolve("attachments").resolve(name).normalize();
            if (!to.startsWith(root)) throw new BusinessException("修订版附件路径非法");
            try { java.nio.file.Files.createDirectories(to.getParent());java.nio.file.Files.copy(from,to); }
            catch(java.io.IOException ex) {throw new BusinessException("修订附件复制失败："+value.getOriginalName());}
            var copy=new com.dsf.radiology.entity.RadiologyAttachment();
            copy.setCaseId(target.getId());copy.setCategory(value.getCategory());copy.setOriginalName(value.getOriginalName());
            copy.setStoredName(name);copy.setStoragePath(to.toString());copy.setMediaType(value.getMediaType());
            copy.setFileSize(value.getFileSize());copy.setSha256(value.getSha256());
            copy.setUploadedBy(operator(null));copy.setUploadedAt(java.time.LocalDateTime.now());attachmentRepository.save(copy);
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
        requirePermission("RADIOLOGY:RULE:MANAGE");
        if (id!=null && rule.getRuleVersion().startsWith("WS-")) throw new BusinessException("内置标准规则不可修改，请复制成独立版本");
        if (request.getRuleVersion().startsWith("WS-")) throw new BusinessException("内置标准规则不可覆盖，请创建独立规则版本");
        if (request.getLowerLimit()==null && request.getUpperLimit()==null && !StringUtils.hasText(request.getExpectedText()))
            throw new BusinessException("判定规则至少需要一项限值或预期文本");
        if (request.getLowerLimit()!=null && request.getUpperLimit()!=null && request.getLowerLimit().compareTo(request.getUpperLimit())>0)
            throw new BusinessException("下限不能大于上限");
        rule.setDetectionCategory(request.getDetectionCategory());
        rule.setLowerInclusive(request.getLowerInclusive());
        rule.setUpperInclusive(request.getUpperInclusive());
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
        requirePermission("RADIOLOGY:RULE:MANAGE");
        if (rule.getRuleVersion().startsWith("WS-")) throw new BusinessException("内置标准规则不可删除");
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

    public record ApprovedSnapshot(RadiologyCase inspectionCase, List<RadiologyMeasurement> measurements,
            List<RadiologyProtectionPoint> protectionPoints, List<com.dsf.radiology.entity.RadiologyAttachment> attachments,
            List<RadiologyDto.ItemCalculationResponse> itemResults, String templateVersion, String templateSha256) {}

    @Transactional
    public RadiologyDto.CaseDetail returnForCorrection(Long caseId, RadiologyDto.WorkflowRequest request) {
        RadiologyCase entity=requireCase(caseId);
        if (entity.getStatus()!=RadiologyCaseStatus.PENDING_REVIEW && entity.getStatus()!=RadiologyCaseStatus.REVIEWED)
            throw new BusinessException("仅待复核或待签发案例可以退回");
        requirePermission(entity.getStatus()==RadiologyCaseStatus.PENDING_REVIEW ?
                "RADIOLOGY:WORKFLOW:REVIEW" : "RADIOLOGY:WORKFLOW:APPROVE");
        checkVersion(entity,request.getExpectedDataVersion());
        if (!StringUtils.hasText(request.getComment())) throw new BusinessException("退回意见必填");
        workflowEvent(entity,"RETURN",request.getComment());
        entity.setReviewerName(null);
        entity.setApproverName(null);
        entity.setApprovedSnapshotJson(null);
        invalidate(entity);
        caseRepository.saveAndFlush(entity);
        return detail(entity);
    }

    public long dataVersion(RadiologyCase entity) { return entity.getDataVersion()==null ? 0 : entity.getDataVersion(); }
    public void checkVersion(RadiologyCase entity, Long expected) {
        if (expected!=null && expected!=dataVersion(entity)) throw new BusinessException("数据已被其他操作修改，请刷新案例后重试");
    }
    public void invalidate(RadiologyCase entity) {
        entity.setDataVersion(dataVersion(entity)+1);
        entity.setCalculatedDataVersion(null);
        entity.setCalculationJson(null);
        entity.setStatus(RadiologyCaseStatus.DATA_ENTERED);
        for (var measurement:measurementRepository.findByCaseIdAndDeletedFalseOrderByItemCodeAscSortNoAsc(entity.getId())) {
            if (measurement.getFieldRole()!=RadiologyFieldRole.RAW) measurement.setDeleted(true);
        }
        for (var point:protectionPointRepository.findByCaseIdAndDeletedFalseOrderBySortNoAsc(entity.getId())) {
            point.setResultValue(null);point.setAverageValue(null);point.setAnnualEffectiveDose(null);
            point.setJudgement(RadiologyJudgement.INCOMPLETE);
        }
        caseRepository.save(entity);
    }
    public List<RadiologyDto.ItemCalculationResponse> itemResults(RadiologyCase entity) {
        if (!StringUtils.hasText(entity.getCalculationJson())) return List.of();
        try { return objectMapper.readValue(entity.getCalculationJson(),new TypeReference<List<RadiologyDto.ItemCalculationResponse>>(){}); }
        catch (JsonProcessingException ex) { throw new BusinessException("计算记录格式损坏"); }
    }
    private List<RadiologyDto.WorkflowEvent> workflowHistory(RadiologyCase entity) {
        if (!StringUtils.hasText(entity.getWorkflowHistoryJson())) return new ArrayList<>();
        try { return objectMapper.readValue(entity.getWorkflowHistoryJson(),new TypeReference<List<RadiologyDto.WorkflowEvent>>(){}); }
        catch (JsonProcessingException ex) { throw new BusinessException("审批记录格式损坏"); }
    }
    private void workflowEvent(RadiologyCase entity,String action,String comment) {
        var events=workflowHistory(entity);
        events.add(new RadiologyDto.WorkflowEvent(action,operator(null),comment,dataVersion(entity),java.time.LocalDateTime.now()));
        entity.setWorkflowHistoryJson(writeJson(events));
    }
    private boolean calculationFresh(RadiologyCase entity) {
        return Objects.equals(entity.getCalculatedDataVersion(),dataVersion(entity))
                && RadiologyCatalogService.CALCULATION_VERSION.equals(entity.getCalculationVersion())
                && Objects.equals(entity.getRuleFingerprint(),ruleFingerprint(entity));
    }
    private String ruleFingerprint(RadiologyCase entity) {
        String text=ruleRepository.findByDeletedFalseAndModalityOrderByItemCodeAsc(entity.getModality()).stream()
                .filter(r -> Boolean.TRUE.equals(r.getEnabled()) && !r.getRuleVersion().startsWith("WS-"))
                .map(r -> r.getId()+":"+r.getRuleVersion()+":"+r.getUpdatedAt())
                .sorted().collect(Collectors.joining("|"));
        try { return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
    public com.dsf.radiology.model.RadiologyProfile profile(RadiologyCase entity) {
        if(!StringUtils.hasText(entity.getProfileJson())) {
            var profile=new com.dsf.radiology.model.RadiologyProfile();
            if("RESPONSE_CORRECTED".equals(entity.getProtectionMode()))profile.setCalibrationMode("BOTH");return profile;
        }
        try {return objectMapper.readValue(entity.getProfileJson(),com.dsf.radiology.model.RadiologyProfile.class);}
        catch(JsonProcessingException ex){throw new BusinessException("报告资料格式损坏");}
    }
    public boolean groupSelected(RadiologyCase entity,String code) {
        var groups=profile(entity).getSelectedGroups();if(groups==null) return true;
        String group=code.equals("RPD") ? "PROTECTION" : entity.getModality()==RadiologyModality.CT ? "QC" :
                List.of("DDI","STP","RUE","REA","ILA","ART","HCR","LCR").contains(code) ? "SPECIAL":"GENERAL";
        return groups.contains(group);
    }
    public String suggestedConclusion(RadiologyCase entity) {
        var failures=itemResults(entity).stream().filter(r->r.judgement()==RadiologyJudgement.UNQUALIFIED)
            .map(r->r.itemName()+"（"+r.ruleDescription()+"）").collect(Collectors.toList());
        protectionPointRepository.findByCaseIdAndDeletedFalseOrderBySortNoAsc(entity.getId()).stream()
            .filter(p->p.getJudgement()==RadiologyJudgement.UNQUALIFIED).forEach(p->failures.add("防护点位："+p.getPointName()));
        return failures.isEmpty() ? "本次未发现不符合项，请结合检测范围确认总体评价。" : "本次不符合项：\n"+String.join("；\n",failures);
    }
    public Map<String,String> itemConditions(RadiologyCase entity) {
        if (!StringUtils.hasText(entity.getItemConditionsJson())) return Map.of();
        try { return objectMapper.readValue(entity.getItemConditionsJson(),new TypeReference<Map<String,String>>(){}); }
        catch (JsonProcessingException ex) { throw new BusinessException("检测条件记录格式损坏"); }
    }

    public List<String> submissionIssues(RadiologyCase entity) {
        List<String> issues=new ArrayList<>();
        if (!calculationFresh(entity)) issues.add("原始数据或判定规则已变更，需要重新计算");
        if (!StringUtils.hasText(entity.getSampleAcceptanceNo())) issues.add("受理编号未填写");
        if(entity.getInspectionDate()==null) issues.add("检测开始日期未填写");
        if(!StringUtils.hasText(entity.getDetectionCategory())) issues.add("检测类别未填写");
        var results=itemResults(entity);
        for (var item:catalogService.getCatalog(entity.getModality())) {
            var result=results.stream().filter(r->r.itemCode().equals(item.code())).findFirst().orElse(null);
            if (result!=null && result.judgement()!=RadiologyJudgement.NOT_APPLICABLE && !item.code().equals("RPD")
                    && !StringUtils.hasText(itemConditions(entity).get(item.code()))) issues.add(item.name()+"缺少检测条件");
            if (result==null) issues.add(item.name()+"尚未计算");
            else if (!result.complete()) issues.add(item.name()+"："+result.message());
            else if (result.judgement()==RadiologyJudgement.NOT_CONFIGURED || result.judgement()==RadiologyJudgement.NEEDS_ASSESSMENT
                    || result.judgement()==RadiologyJudgement.INCOMPLETE) issues.add(item.name()+"："+result.ruleDescription());
        }
        var points=protectionPointRepository.findByCaseIdAndDeletedFalseOrderBySortNoAsc(entity.getId());
        if (groupSelected(entity,"RPD") && points.isEmpty()) issues.add("尚未录入场所防护点位");
        for (var point:points) if (point.getResultValue()==null || point.getJudgement()==RadiologyJudgement.INCOMPLETE
                || point.getJudgement()==RadiologyJudgement.NEEDS_ASSESSMENT) issues.add(point.getPointName()+"需完成检测或年有效剂量评价");
        return issues;
    }
    public void requirePermission(String code) {
        if (AuthContext.get()!=null && !accessService.hasPermission(AuthContext.get(),code))
            throw new BusinessException("无权限执行此操作："+code);
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
        measurement.setValueText(valueNum!=null ? valueNum.toPlainString() : StringUtils.hasText(valueText) ? valueText.trim() : null);
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
        measurement.setDisplayScale(field.displayScale());
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
        if(!groupSelected(entity,"RPD"))return;
        Map<String, RadiologyMeasurement> rpd = measurementRepository
                .findByCaseIdAndItemCodeAndDeletedFalseOrderBySortNoAsc(entity.getId(), "RPD").stream()
                .filter(value -> value.getFieldRole() == RadiologyFieldRole.RAW)
                .collect(Collectors.toMap(RadiologyMeasurement::getFieldCode, value -> value, (left, right) -> right));
        BigDecimal k = Optional.ofNullable(rpd.get("k")).map(RadiologyMeasurement::getValueNum).orElse(BigDecimal.ONE);
        BigDecimal a = switch(entity.getRpdUnit()==null ? "nSv/h" : entity.getRpdUnit()) {
            case "μSv/h" -> BigDecimal.ONE;
            case "mSv/h" -> new BigDecimal("1000");
            default -> new BigDecimal("0.001");
        };
        if (k.signum()<=0) throw new BusinessException("仪器校准因子必须大于0");
        String mode=entity.getProtectionMode()==null ? "UNSUBTRACTED" : entity.getProtectionMode();
        String calibration=profile(entity).getCalibrationMode();
        if(calibration==null || !StringUtils.hasText(entity.getProfileJson())) calibration=mode.equals("RESPONSE_CORRECTED") ? "BOTH":"INSTRUMENT";
        if(List.of("NONE","TIME").contains(calibration)) k=BigDecimal.ONE;
        BigDecimal background=BigDecimal.ZERO;
        if (!mode.equals("UNSUBTRACTED")) {
            List<BigDecimal> rawBackground=new ArrayList<>();
            for (int i=1;i<=10;i++) {
                var reading=rpd.get("RPD0-"+i);
                if (reading==null || reading.getValueNum()==null) throw new BusinessException("扣本底方式必须录齐10次本底读数");
                rawBackground.add(reading.getValueNum());
            }
            background=rawBackground.stream().reduce(BigDecimal.ZERO,BigDecimal::add).divide(BigDecimal.TEN);
        }
        BigDecimal responseFactor=BigDecimal.ONE;
        if (List.of("BOTH","TIME").contains(calibration)) {
            responseFactor=Optional.ofNullable(rpd.get("RPD-k2")).map(RadiologyMeasurement::getValueNum).orElse(null);
            if (responseFactor==null || responseFactor.signum()<=0) throw new BusinessException("响应时间修正必须提供有效k2，按GBZ 130附录B核定");
        }
        BigDecimal limit = entity.getModality() == RadiologyModality.DR ? new BigDecimal("25") : new BigDecimal("2.5");
        BigDecimal normalizationFactor = BigDecimal.ONE;
        if (entity.getModality() == RadiologyModality.DR) {
            BigDecimal testCurrent = Optional.ofNullable(rpd.get("RPD-mA"))
                    .map(RadiologyMeasurement::getValueNum).orElse(new BigDecimal("100"));
            if (testCurrent.signum() <= 0) throw new BusinessException("DR 防护检测管电流必须大于 0 mA");
            normalizationFactor = new BigDecimal("100").divide(testCurrent, 12, java.math.RoundingMode.HALF_UP);
        }
        for (RadiologyProtectionPoint point : protectionPointRepository.findByCaseIdAndDeletedFalseOrderBySortNoAsc(entity.getId())) {
            List<BigDecimal> pointReadings=readReadings(point.getReadingsJson());
            List<BigDecimal> calculated=calculationEngine.calculateProtectionReadings(pointReadings,k,a,normalizationFactor);
            if (!calculated.isEmpty() && !mode.equals("UNSUBTRACTED")) {
                BigDecimal net=calculated.get(0).subtract(background);
                if (net.signum()<0) calculated=List.of();
                else calculated=List.of(calculated.get(0),net.multiply(k).multiply(a).multiply(normalizationFactor).multiply(responseFactor));
            }
            if (!calculated.isEmpty() && mode.equals("UNSUBTRACTED"))
                calculated=List.of(calculated.get(0),calculated.get(1).multiply(responseFactor));
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
        if (rule.getLowerLimit() != null) parts.add((Boolean.FALSE.equals(rule.getLowerInclusive()) ? ">" : "≥") + rule.getLowerLimit());
        if (rule.getUpperLimit() != null) parts.add((Boolean.FALSE.equals(rule.getUpperInclusive()) ? "<" : "≤") + rule.getUpperLimit());
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
        if ((Boolean.FALSE.equals(request.getHasAec()) || Boolean.FALSE.equals(request.getHasRawImages()))
                && !StringUtils.hasText(request.getFeatureReason())) throw new BusinessException("缺少设备功能/图像条件的不检测理由");
        if (request.getModality()==RadiologyModality.CT && request.getEquipmentName()!=null
                && request.getEquipmentName().toUpperCase(java.util.Locale.ROOT).contains("CBCT"))
            throw new BusinessException("牙科CBCT不适用普通CT规则，请使用专门业务模块");
        if (request.getInspectionEndDate()!=null && request.getInspectionDate()!=null
                && request.getInspectionEndDate().isBefore(request.getInspectionDate())) throw new BusinessException("检测结束日期不能早于开始日期");
        if (!List.of("UNSUBTRACTED","SUBTRACTED","RESPONSE_CORRECTED").contains(request.getProtectionMode()==null ? "UNSUBTRACTED":request.getProtectionMode()))
            throw new BusinessException("不支持的防护本底处理方式");
        if (!List.of("nSv/h","μSv/h","mSv/h").contains(request.getRpdUnit()==null ? "nSv/h":request.getRpdUnit()))
            throw new BusinessException("不支持的防护读数单位");
        entity.setHasAec(request.getHasAec());
        entity.setHasRawImages(request.getHasRawImages());
        entity.setFeatureReason(request.getFeatureReason());
        entity.setAgencyName(request.getAgencyName());
        entity.setRegion(request.getRegion());
        entity.setCity(request.getCity());entity.setCounty(request.getCounty());
        if(request.getProfile()!=null) {
            var profile=request.getProfile();
            if(!StringUtils.hasText(request.getSampleAcceptanceNo()) || !StringUtils.hasText(request.getDetectionCategory()))throw new BusinessException("受理编号与检测类别为必填项");
            if(request.getInspectionDate()==null || request.getInspectionEndDate()==null)throw new BusinessException("检测起止日期为必填项");
            if(profile.getSelectedGroups()!=null && (profile.getSelectedGroups().isEmpty() || !(request.getModality()==RadiologyModality.DR ? List.of("GENERAL","SPECIAL","PROTECTION"):List.of("QC","PROTECTION")).containsAll(profile.getSelectedGroups())))
                throw new BusinessException("至少选择一组检测项目，并使用受支持的检测分组");
            if(profile.getCalibrationMode()!=null && !List.of("NONE","INSTRUMENT","BOTH","TIME").contains(profile.getCalibrationMode()))
                throw new BusinessException("不支持的校准组合");
            entity.setProfileJson(writeJson(profile));
        }
        entity.setInspectionEndDate(request.getInspectionEndDate());
        entity.setProtectionMode(request.getProtectionMode()==null ? "UNSUBTRACTED":request.getProtectionMode());
        entity.setRpdUnit(request.getRpdUnit()==null ? "nSv/h":request.getRpdUnit());
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
        target.setItemConditionsJson(source.getItemConditionsJson());
        target.setHasAec(source.getHasAec());target.setHasRawImages(source.getHasRawImages());
        target.setFeatureReason(source.getFeatureReason());target.setAgencyName(source.getAgencyName());
        target.setCity(source.getCity());target.setCounty(source.getCounty());target.setProfileJson(source.getProfileJson());
        target.setRegion(source.getRegion());target.setInspectionEndDate(source.getInspectionEndDate());
        target.setProtectionMode(source.getProtectionMode());target.setRpdUnit(source.getRpdUnit());
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
                entity.getCreatedAt(), entity.getUpdatedAt(),entity.getSampleAcceptanceNo(),entity.getEquipmentNo(),
                entity.getReportDate(),entity.getCity(),entity.getCounty(),reportRepository.findFirstByCaseIdAndDeletedFalseOrderByGeneratedAtDesc(entity.getId()).map(com.dsf.radiology.entity.RadiologyReport::getGeneratedAt).orElse(null));
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
                entity.getCreatedAt(), entity.getUpdatedAt(), dataVersion(entity), entity.getCalculatedDataVersion(),
                calculationFresh(entity),entity.getHasAec(),entity.getHasRawImages(),entity.getFeatureReason(),
                entity.getAgencyName(),entity.getRegion(),entity.getInspectionEndDate(),entity.getProtectionMode(),entity.getRpdUnit(),
                itemResults(entity),workflowHistory(entity),submissionIssues(entity),itemConditions(entity),entity.getCity(),entity.getCounty(),profile(entity));
    }

    private RadiologyDto.MeasurementResponse measurementResponse(RadiologyMeasurement value) {
        return new RadiologyDto.MeasurementResponse(
                value.getId(), value.getItemCode(), value.getFieldCode(), value.getFieldLabel(), value.getFieldRole(),
                value.getValueNum(), value.getValueText(), value.getUnit(), value.getSortNo(), value.getValueSource(),value.getValueText()!=null ? value.getValueText() : value.getValueNum()==null ? "" : value.getValueNum().setScale(value.getDisplayScale()==null ? 6:value.getDisplayScale(), java.math.RoundingMode.HALF_EVEN).toPlainString());
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
                value.getStandardName(), value.getEnabled(),value.getDetectionCategory(),value.getLowerInclusive(),value.getUpperInclusive());
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
