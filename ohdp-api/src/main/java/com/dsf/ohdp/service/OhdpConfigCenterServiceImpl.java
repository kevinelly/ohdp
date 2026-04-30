package com.dsf.ohdp.service;

import com.dsf.common.exception.BusinessException;
import com.dsf.ohdp.dto.ConfigCenterDto;
import com.dsf.ohdp.entity.OhdpDetectType;
import com.dsf.ohdp.entity.OhdpDetectionMethod;
import com.dsf.ohdp.entity.OhdpOrgConfig;
import com.dsf.ohdp.entity.OhdpReportTemplateCatalog;
import com.dsf.ohdp.repository.OhdpDetectTypeRepository;
import com.dsf.ohdp.repository.OhdpDetectionMethodRepository;
import com.dsf.ohdp.repository.OhdpOrgConfigRepository;
import com.dsf.ohdp.repository.OhdpReportTemplateCatalogRepository;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class OhdpConfigCenterServiceImpl implements OhdpConfigCenterService {

    private final OhdpDetectionMethodRepository detectionMethodRepository;
    private final OhdpOrgConfigRepository orgConfigRepository;
    private final OhdpDetectTypeRepository detectTypeRepository;
    private final OhdpReportTemplateCatalogRepository reportCatalogRepository;

    // ==================== 检测方法 ====================

    @Override
    @Transactional(readOnly = true)
    public Page<ConfigCenterDto.DetectionMethodResponse> pageDetectionMethods(ConfigCenterDto.DetectionMethodQuery query) {
        Pageable pageable = PageRequest.of(query.getPage(), query.getSize(), Sort.by(Sort.Direction.ASC, "id"));
        return detectionMethodRepository.findByPage(query.getKeyword(), query.getEnabled(), pageable)
                .map(this::toDetectionMethodResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ConfigCenterDto.DetectionMethodResponse> listAllDetectionMethods() {
        return detectionMethodRepository.findAllByDeletedFalseAndEnabledTrueOrderByIdAsc()
                .stream()
                .map(this::toDetectionMethodResponse)
                .collect(Collectors.toList());
    }

    @Override
    public ConfigCenterDto.DetectionMethodResponse createDetectionMethod(ConfigCenterDto.DetectionMethodRequest request) {
        OhdpDetectionMethod entity = new OhdpDetectionMethod();
        applyDetectionMethodRequest(entity, request);
        return toDetectionMethodResponse(detectionMethodRepository.save(entity));
    }

    @Override
    public ConfigCenterDto.DetectionMethodResponse updateDetectionMethod(Long id, ConfigCenterDto.DetectionMethodRequest request) {
        OhdpDetectionMethod entity = detectionMethodRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BusinessException("检测方法不存在: " + id));
        applyDetectionMethodRequest(entity, request);
        return toDetectionMethodResponse(detectionMethodRepository.save(entity));
    }

    @Override
    public void deleteDetectionMethod(Long id) {
        OhdpDetectionMethod entity = detectionMethodRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BusinessException("检测方法不存在: " + id));
        entity.setDeleted(true);
        detectionMethodRepository.save(entity);
    }

    private void applyDetectionMethodRequest(OhdpDetectionMethod entity, ConfigCenterDto.DetectionMethodRequest request) {
        if (request.getMethodName() != null) entity.setMethodName(request.getMethodName());
        if (request.getMethodCode() != null) entity.setMethodCode(request.getMethodCode());
        if (request.getMethodAbbr() != null) entity.setMethodAbbr(request.getMethodAbbr());
        if (request.getFormula() != null) entity.setFormula(request.getFormula());
        if (request.getFormulaCode() != null) entity.setFormulaCode(request.getFormulaCode());
        if (request.getStandardCode() != null) entity.setStandardCode(request.getStandardCode());
        if (request.getResultDecimalPlaces() != null) entity.setResultDecimalPlaces(request.getResultDecimalPlaces());
        if (request.getCalcDecimalPlaces() != null) entity.setCalcDecimalPlaces(request.getCalcDecimalPlaces());
        if (request.getEnabled() != null) entity.setEnabled(request.getEnabled());
    }

    private ConfigCenterDto.DetectionMethodResponse toDetectionMethodResponse(OhdpDetectionMethod entity) {
        return ConfigCenterDto.DetectionMethodResponse.builder()
                .id(entity.getId())
                .methodName(entity.getMethodName())
                .methodCode(entity.getMethodCode())
                .methodAbbr(entity.getMethodAbbr())
                .formula(entity.getFormula())
                .formulaCode(entity.getFormulaCode())
                .standardCode(entity.getStandardCode())
                .resultDecimalPlaces(entity.getResultDecimalPlaces())
                .calcDecimalPlaces(entity.getCalcDecimalPlaces())
                .enabled(entity.getEnabled())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    // ==================== 机构配置 ====================

    @Override
    @Transactional(readOnly = true)
    public ConfigCenterDto.OrgConfigResponse getOrgConfig() {
        return orgConfigRepository.findFirstByDeletedFalse()
                .map(this::toOrgConfigResponse)
                .orElseGet(() -> ConfigCenterDto.OrgConfigResponse.builder().build());
    }

    @Override
    public ConfigCenterDto.OrgConfigResponse saveOrgConfig(ConfigCenterDto.OrgConfigRequest request) {
        OhdpOrgConfig entity = orgConfigRepository.findFirstByDeletedFalse()
                .orElseGet(OhdpOrgConfig::new);
        applyOrgConfigRequest(entity, request);
        return toOrgConfigResponse(orgConfigRepository.save(entity));
    }

    private void applyOrgConfigRequest(OhdpOrgConfig entity, ConfigCenterDto.OrgConfigRequest request) {
        if (request.getOrgName() != null) entity.setOrgName(request.getOrgName());
        if (request.getOrgCode() != null) entity.setOrgCode(request.getOrgCode());
        if (request.getOrgAddress() != null) entity.setOrgAddress(request.getOrgAddress());
        if (request.getContactName() != null) entity.setContactName(request.getContactName());
        if (request.getContactPhone() != null) entity.setContactPhone(request.getContactPhone());
        if (request.getCertNo() != null) entity.setCertNo(request.getCertNo());
        if (request.getCertFileId() != null) entity.setCertFileId(request.getCertFileId());
        if (request.getSampleNoPrefix() != null) entity.setSampleNoPrefix(request.getSampleNoPrefix());
        if (request.getSampleNoYearFormat() != null) entity.setSampleNoYearFormat(request.getSampleNoYearFormat());
        if (request.getControlledNoPrefix() != null) entity.setControlledNoPrefix(request.getControlledNoPrefix());
        if (request.getReportNoPrefix() != null) entity.setReportNoPrefix(request.getReportNoPrefix());
        if (request.getReportNoYearFormat() != null) entity.setReportNoYearFormat(request.getReportNoYearFormat());
    }

    private ConfigCenterDto.OrgConfigResponse toOrgConfigResponse(OhdpOrgConfig entity) {
        return ConfigCenterDto.OrgConfigResponse.builder()
                .id(entity.getId())
                .orgName(entity.getOrgName())
                .orgCode(entity.getOrgCode())
                .orgAddress(entity.getOrgAddress())
                .contactName(entity.getContactName())
                .contactPhone(entity.getContactPhone())
                .certNo(entity.getCertNo())
                .certFileId(entity.getCertFileId())
                .sampleNoPrefix(entity.getSampleNoPrefix())
                .sampleNoYearFormat(entity.getSampleNoYearFormat())
                .controlledNoPrefix(entity.getControlledNoPrefix())
                .reportNoPrefix(entity.getReportNoPrefix())
                .reportNoYearFormat(entity.getReportNoYearFormat())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    // ==================== 检测分类 ====================

    @Override
    @Transactional(readOnly = true)
    public List<ConfigCenterDto.DetectTypeResponse> listDetectTypes(Boolean enabled) {
        List<OhdpDetectType> list;
        if (Boolean.TRUE.equals(enabled)) {
            list = detectTypeRepository.findAllByDeletedFalseAndEnabledTrueOrderBySortNoAsc();
        } else {
            list = detectTypeRepository.findAllByDeletedFalseOrderBySortNoAsc();
        }
        return list.stream().map(this::toDetectTypeResponse).collect(Collectors.toList());
    }

    @Override
    public ConfigCenterDto.DetectTypeResponse createDetectType(ConfigCenterDto.DetectTypeRequest request) {
        OhdpDetectType entity = new OhdpDetectType();
        applyDetectTypeRequest(entity, request);
        return toDetectTypeResponse(detectTypeRepository.save(entity));
    }

    @Override
    public ConfigCenterDto.DetectTypeResponse updateDetectType(Long id, ConfigCenterDto.DetectTypeRequest request) {
        OhdpDetectType entity = detectTypeRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BusinessException("检测分类不存在: " + id));
        applyDetectTypeRequest(entity, request);
        return toDetectTypeResponse(detectTypeRepository.save(entity));
    }

    @Override
    public void deleteDetectType(Long id) {
        OhdpDetectType entity = detectTypeRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BusinessException("检测分类不存在: " + id));
        entity.setDeleted(true);
        detectTypeRepository.save(entity);
    }

    private void applyDetectTypeRequest(OhdpDetectType entity, ConfigCenterDto.DetectTypeRequest request) {
        if (request.getTypeName() != null) entity.setTypeName(request.getTypeName());
        if (request.getTypeCode() != null) entity.setTypeCode(request.getTypeCode());
        if (request.getTypeAbbr() != null) entity.setTypeAbbr(request.getTypeAbbr());
        if (request.getSortNo() != null) entity.setSortNo(request.getSortNo());
        if (request.getEnabled() != null) entity.setEnabled(request.getEnabled());
    }

    private ConfigCenterDto.DetectTypeResponse toDetectTypeResponse(OhdpDetectType entity) {
        return ConfigCenterDto.DetectTypeResponse.builder()
                .id(entity.getId())
                .typeName(entity.getTypeName())
                .typeCode(entity.getTypeCode())
                .typeAbbr(entity.getTypeAbbr())
                .sortNo(entity.getSortNo())
                .enabled(entity.getEnabled())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    // ==================== 报告模板目录 ====================

    @Override
    @Transactional(readOnly = true)
    public List<ConfigCenterDto.ReportCatalogResponse> listReportCatalogs(Long detectTypeId) {
        List<OhdpReportTemplateCatalog> list;
        if (detectTypeId != null) {
            list = reportCatalogRepository.findByDetectTypeIdAndDeletedFalseOrderBySortNoAsc(detectTypeId);
        } else {
            list = reportCatalogRepository.findAllByDeletedFalseOrderBySortNoAsc();
        }
        return list.stream().map(this::toReportCatalogResponse).collect(Collectors.toList());
    }

    @Override
    public ConfigCenterDto.ReportCatalogResponse createReportCatalog(ConfigCenterDto.ReportCatalogRequest request) {
        OhdpReportTemplateCatalog entity = new OhdpReportTemplateCatalog();
        applyReportCatalogRequest(entity, request);
        return toReportCatalogResponse(reportCatalogRepository.save(entity));
    }

    @Override
    public ConfigCenterDto.ReportCatalogResponse updateReportCatalog(Long id, ConfigCenterDto.ReportCatalogRequest request) {
        OhdpReportTemplateCatalog entity = reportCatalogRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BusinessException("报告模板目录不存在: " + id));
        applyReportCatalogRequest(entity, request);
        return toReportCatalogResponse(reportCatalogRepository.save(entity));
    }

    @Override
    public void deleteReportCatalog(Long id) {
        OhdpReportTemplateCatalog entity = reportCatalogRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BusinessException("报告模板目录不存在: " + id));
        List<OhdpReportTemplateCatalog> children = reportCatalogRepository.findByParentIdAndDeletedFalse(id);
        if (!children.isEmpty()) {
            throw new BusinessException("该目录下存在子节点，无法删除");
        }
        entity.setDeleted(true);
        reportCatalogRepository.save(entity);
    }

    private void applyReportCatalogRequest(OhdpReportTemplateCatalog entity, ConfigCenterDto.ReportCatalogRequest request) {
        if (request.getDetectTypeId() != null) entity.setDetectTypeId(request.getDetectTypeId());
        if (request.getCatalogName() != null) entity.setCatalogName(request.getCatalogName());
        if (request.getCatalogCode() != null) entity.setCatalogCode(request.getCatalogCode());
        if (request.getParentId() != null) entity.setParentId(request.getParentId());
        if (request.getSortNo() != null) entity.setSortNo(request.getSortNo());
        if (request.getIsRequired() != null) entity.setIsRequired(request.getIsRequired());
        if (request.getNotes() != null) entity.setNotes(request.getNotes());
    }

    private ConfigCenterDto.ReportCatalogResponse toReportCatalogResponse(OhdpReportTemplateCatalog entity) {
        return ConfigCenterDto.ReportCatalogResponse.builder()
                .id(entity.getId())
                .detectTypeId(entity.getDetectTypeId())
                .catalogName(entity.getCatalogName())
                .catalogCode(entity.getCatalogCode())
                .parentId(entity.getParentId())
                .sortNo(entity.getSortNo())
                .isRequired(entity.getIsRequired())
                .notes(entity.getNotes())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
