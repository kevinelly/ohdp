package com.dsf.ohdp.service;

import com.dsf.common.exception.BusinessException;
import com.dsf.ohdp.dto.HazardFactorDto;
import com.dsf.ohdp.entity.OhdpHazardFactor;
import com.dsf.ohdp.entity.OhdpHazardFactorLimit;
import com.dsf.ohdp.entity.OhdpHazardFactorMethod;
import com.dsf.ohdp.entity.OhdpSds;
import com.dsf.ohdp.repository.OhdpHazardFactorLimitRepository;
import com.dsf.ohdp.repository.OhdpHazardFactorMethodRepository;
import com.dsf.ohdp.repository.OhdpHazardFactorRepository;
import com.dsf.ohdp.repository.OhdpSdsRepository;
import com.dsf.ohdp.support.OhdpCodeLabels;
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
public class OhdpHazardFactorServiceImpl implements OhdpHazardFactorService {

    private final OhdpHazardFactorRepository hazardFactorRepo;
    private final OhdpHazardFactorLimitRepository limitRepo;
    private final OhdpHazardFactorMethodRepository methodRepo;
    private final OhdpSdsRepository sdsRepo;

    // -------------------------------------------------------------------------
    // HazardFactor
    // -------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public Page<HazardFactorDto.HazardFactorResponse> pageHazardFactors(
            String keyword, String factorCategory, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("factorCategory").ascending().and(Sort.by("sortNo").ascending()));
        Page<OhdpHazardFactor> entityPage;
        boolean hasKeyword = keyword != null && !keyword.isBlank();
        boolean hasCategory = factorCategory != null && !factorCategory.isBlank();
        if (hasKeyword && hasCategory) {
            entityPage = hazardFactorRepo.findByDeletedFalseAndFactorNameContainingAndFactorCategory(keyword, factorCategory, pageable);
        } else if (hasKeyword) {
            entityPage = hazardFactorRepo.findByDeletedFalseAndFactorNameContaining(keyword, pageable);
        } else if (hasCategory) {
            entityPage = hazardFactorRepo.findByDeletedFalseAndFactorCategory(factorCategory, pageable);
        } else {
            entityPage = hazardFactorRepo.findByDeletedFalse(pageable);
        }
        return entityPage.map(this::toHazardFactorResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public HazardFactorDto.HazardFactorDetailResponse getHazardFactor(Long id) {
        OhdpHazardFactor entity = hazardFactorRepo.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BusinessException("危害因素不存在: " + id));
        List<HazardFactorDto.HazardFactorLimitResponse> limits =
                limitRepo.findByFactorIdAndDeletedFalseOrderByLimitTypeAsc(id)
                        .stream()
                        .map(this::toLimitResponse)
                        .collect(Collectors.toList());
        return HazardFactorDto.HazardFactorDetailResponse.builder()
                .id(entity.getId())
                .factorName(entity.getFactorName())
                .factorCode(entity.getFactorCode())
                .factorCategory(entity.getFactorCategory())
                .factorCategoryLabel(OhdpCodeLabels.label(entity.getFactorCategory()))
                .casNo(entity.getCasNo())
                .englishName(entity.getEnglishName())
                .sortNo(entity.getSortNo())
                .enabled(entity.getEnabled())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .limits(limits)
                .build();
    }

    @Override
    public HazardFactorDto.HazardFactorResponse createHazardFactor(HazardFactorDto.HazardFactorRequest request) {
        OhdpHazardFactor entity = new OhdpHazardFactor();
        applyHazardFactorRequest(entity, request);
        return toHazardFactorResponse(hazardFactorRepo.save(entity));
    }

    @Override
    public HazardFactorDto.HazardFactorResponse updateHazardFactor(Long id, HazardFactorDto.HazardFactorRequest request) {
        OhdpHazardFactor entity = hazardFactorRepo.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BusinessException("危害因素不存在: " + id));
        applyHazardFactorRequest(entity, request);
        return toHazardFactorResponse(hazardFactorRepo.save(entity));
    }

    @Override
    public void deleteHazardFactor(Long id) {
        OhdpHazardFactor entity = hazardFactorRepo.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BusinessException("危害因素不存在: " + id));
        entity.setDeleted(true);
        hazardFactorRepo.save(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public List<HazardFactorDto.HazardFactorResponse> listAllHazardFactors() {
        return hazardFactorRepo.findByDeletedFalseOrderByFactorCategoryAscSortNoAsc()
                .stream()
                .map(this::toHazardFactorResponse)
                .collect(Collectors.toList());
    }

    // -------------------------------------------------------------------------
    // HazardFactorLimit
    // -------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public List<HazardFactorDto.HazardFactorLimitResponse> listLimits(Long factorId) {
        return limitRepo.findByFactorIdAndDeletedFalseOrderByLimitTypeAsc(factorId)
                .stream()
                .map(this::toLimitResponse)
                .collect(Collectors.toList());
    }

    @Override
    public HazardFactorDto.HazardFactorLimitResponse addLimit(Long factorId, HazardFactorDto.HazardFactorLimitRequest request) {
        hazardFactorRepo.findByIdAndDeletedFalse(factorId)
                .orElseThrow(() -> new BusinessException("危害因素不存在: " + factorId));
        OhdpHazardFactorLimit entity = new OhdpHazardFactorLimit();
        entity.setFactorId(factorId);
        applyLimitRequest(entity, request);
        return toLimitResponse(limitRepo.save(entity));
    }

    @Override
    public HazardFactorDto.HazardFactorLimitResponse updateLimit(Long limitId, HazardFactorDto.HazardFactorLimitRequest request) {
        OhdpHazardFactorLimit entity = limitRepo.findByIdAndDeletedFalse(limitId)
                .orElseThrow(() -> new BusinessException("限值不存在: " + limitId));
        applyLimitRequest(entity, request);
        return toLimitResponse(limitRepo.save(entity));
    }

    @Override
    public void deleteLimit(Long limitId) {
        OhdpHazardFactorLimit entity = limitRepo.findByIdAndDeletedFalse(limitId)
                .orElseThrow(() -> new BusinessException("限值不存在: " + limitId));
        entity.setDeleted(true);
        limitRepo.save(entity);
    }

    // -------------------------------------------------------------------------
    // HazardFactorMethod
    // -------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public List<HazardFactorDto.HazardFactorMethodResponse> listMethods(Long factorId) {
        return methodRepo.findByFactorIdAndDeletedFalse(factorId)
                .stream()
                .map(this::toMethodResponse)
                .collect(Collectors.toList());
    }

    @Override
    public HazardFactorDto.HazardFactorMethodResponse addMethod(Long factorId, HazardFactorDto.HazardFactorMethodRequest request) {
        hazardFactorRepo.findByIdAndDeletedFalse(factorId)
                .orElseThrow(() -> new BusinessException("危害因素不存在: " + factorId));
        methodRepo.findByFactorIdAndMethodIdAndDeletedFalse(factorId, request.getMethodId())
                .ifPresent(existing -> {
                    throw new BusinessException("该检测方法已与此危害因素关联");
                });
        OhdpHazardFactorMethod entity = new OhdpHazardFactorMethod();
        entity.setFactorId(factorId);
        entity.setMethodId(request.getMethodId());
        entity.setIsDefault(Boolean.TRUE.equals(request.getIsDefault()));
        return toMethodResponse(methodRepo.save(entity));
    }

    @Override
    public void deleteMethod(Long id) {
        OhdpHazardFactorMethod entity = methodRepo.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BusinessException("检测方法关联不存在: " + id));
        entity.setDeleted(true);
        methodRepo.save(entity);
    }

    // -------------------------------------------------------------------------
    // Sds
    // -------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public Page<HazardFactorDto.SdsResponse> pageSds(String keyword, Long factorId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("id").descending());
        boolean hasKeyword = keyword != null && !keyword.isBlank();
        boolean hasFactorId = factorId != null;
        Page<OhdpSds> entityPage;
        if (hasKeyword && hasFactorId) {
            entityPage = sdsRepo.findByDeletedFalseAndSubstanceNameContainingAndFactorId(keyword, factorId, pageable);
        } else if (hasKeyword) {
            entityPage = sdsRepo.findByDeletedFalseAndSubstanceNameContaining(keyword, pageable);
        } else if (hasFactorId) {
            entityPage = sdsRepo.findByDeletedFalseAndFactorId(factorId, pageable);
        } else {
            entityPage = sdsRepo.findByDeletedFalse(pageable);
        }
        return entityPage.map(this::toSdsResponse);
    }

    @Override
    public HazardFactorDto.SdsResponse createSds(HazardFactorDto.SdsRequest request) {
        OhdpSds entity = new OhdpSds();
        applySdsRequest(entity, request);
        return toSdsResponse(sdsRepo.save(entity));
    }

    @Override
    public HazardFactorDto.SdsResponse updateSds(Long id, HazardFactorDto.SdsRequest request) {
        OhdpSds entity = sdsRepo.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BusinessException("SDS不存在: " + id));
        applySdsRequest(entity, request);
        return toSdsResponse(sdsRepo.save(entity));
    }

    @Override
    public void deleteSds(Long id) {
        OhdpSds entity = sdsRepo.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BusinessException("SDS不存在: " + id));
        entity.setDeleted(true);
        sdsRepo.save(entity);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private void applyHazardFactorRequest(OhdpHazardFactor entity, HazardFactorDto.HazardFactorRequest request) {
        entity.setFactorName(request.getFactorName());
        entity.setFactorCode(request.getFactorCode());
        entity.setFactorCategory(request.getFactorCategory());
        entity.setCasNo(request.getCasNo());
        entity.setEnglishName(request.getEnglishName());
        if (request.getSortNo() != null) {
            entity.setSortNo(request.getSortNo());
        }
        if (request.getEnabled() != null) {
            entity.setEnabled(request.getEnabled());
        }
    }

    private HazardFactorDto.HazardFactorResponse toHazardFactorResponse(OhdpHazardFactor entity) {
        return HazardFactorDto.HazardFactorResponse.builder()
                .id(entity.getId())
                .factorName(entity.getFactorName())
                .factorCode(entity.getFactorCode())
                .factorCategory(entity.getFactorCategory())
                .factorCategoryLabel(OhdpCodeLabels.label(entity.getFactorCategory()))
                .casNo(entity.getCasNo())
                .englishName(entity.getEnglishName())
                .sortNo(entity.getSortNo())
                .enabled(entity.getEnabled())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private void applyLimitRequest(OhdpHazardFactorLimit entity, HazardFactorDto.HazardFactorLimitRequest request) {
        entity.setLimitType(request.getLimitType());
        entity.setLimitValue(request.getLimitValue());
        entity.setLimitUnit(request.getLimitUnit());
        entity.setStandardCode(request.getStandardCode());
        entity.setNotes(request.getNotes());
    }

    private HazardFactorDto.HazardFactorLimitResponse toLimitResponse(OhdpHazardFactorLimit entity) {
        return HazardFactorDto.HazardFactorLimitResponse.builder()
                .id(entity.getId())
                .factorId(entity.getFactorId())
                .limitType(entity.getLimitType())
                .limitTypeLabel(limitTypeLabel(entity.getLimitType()))
                .limitValue(entity.getLimitValue())
                .limitUnit(entity.getLimitUnit())
                .standardCode(entity.getStandardCode())
                .notes(entity.getNotes())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private String limitTypeLabel(String value) {
        if (value == null || value.isBlank()) return "未设置";
        return switch (value.toUpperCase()) {
            case "PC_TWA" -> "时间加权平均容许浓度";
            case "PC_STEL" -> "短时间接触容许浓度";
            case "MAC" -> "最高容许浓度";
            case "PE" -> "峰接触浓度";
            case "LEX8H", "LEX,8H" -> "8 小时等效声级";
            default -> "未知（" + value + "）";
        };
    }

    private HazardFactorDto.HazardFactorMethodResponse toMethodResponse(OhdpHazardFactorMethod entity) {
        return HazardFactorDto.HazardFactorMethodResponse.builder()
                .id(entity.getId())
                .factorId(entity.getFactorId())
                .methodId(entity.getMethodId())
                .isDefault(entity.getIsDefault())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private void applySdsRequest(OhdpSds entity, HazardFactorDto.SdsRequest request) {
        entity.setFactorId(request.getFactorId());
        entity.setSubstanceName(request.getSubstanceName());
        entity.setCasNo(request.getCasNo());
        entity.setVersion(request.getVersion());
        entity.setFileId(request.getFileId());
        entity.setNotes(request.getNotes());
    }

    private HazardFactorDto.SdsResponse toSdsResponse(OhdpSds entity) {
        return HazardFactorDto.SdsResponse.builder()
                .id(entity.getId())
                .factorId(entity.getFactorId())
                .substanceName(entity.getSubstanceName())
                .casNo(entity.getCasNo())
                .version(entity.getVersion())
                .fileId(entity.getFileId())
                .notes(entity.getNotes())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
