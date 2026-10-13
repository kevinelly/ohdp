package com.dsf.ohdp.service;

import com.dsf.common.exception.BusinessException;
import com.dsf.ohdp.dto.BasicDataDto;
import com.dsf.ohdp.entity.OhdpDevice;
import com.dsf.ohdp.entity.OhdpLaw;
import com.dsf.ohdp.entity.OhdpRegion;
import com.dsf.ohdp.entity.OhdpStandard;
import com.dsf.ohdp.repository.OhdpDeviceRepository;
import com.dsf.ohdp.repository.OhdpLawRepository;
import com.dsf.ohdp.repository.OhdpRegionRepository;
import com.dsf.ohdp.repository.OhdpStandardRepository;
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
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Transactional
public class OhdpBasicDataServiceImpl implements OhdpBasicDataService {

    private final OhdpLawRepository lawRepository;
    private final OhdpStandardRepository standardRepository;
    private final OhdpRegionRepository regionRepository;
    private final OhdpDeviceRepository deviceRepository;
    private final OhdpReferenceDocumentService referenceDocumentService;

    // ==================== 法律法规 ====================

    @Override
    @Transactional(readOnly = true)
    public Page<BasicDataDto.LawResponse> pageLaws(String keyword, String level, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
        boolean hasKeyword = StringUtils.hasText(keyword);
        boolean hasLevel = StringUtils.hasText(level);
        Page<OhdpLaw> result;
        if (hasKeyword && hasLevel) {
            result = lawRepository.findByTitleContainingIgnoreCaseAndLevelAndDeletedFalse(keyword, level, pageable);
        } else if (hasKeyword) {
            result = lawRepository.findByTitleContainingIgnoreCaseAndDeletedFalse(keyword, pageable);
        } else if (hasLevel) {
            result = lawRepository.findByLevelAndDeletedFalse(level, pageable);
        } else {
            result = lawRepository.findByDeletedFalse(pageable);
        }
        return result.map(this::toLawResponse);
    }

    @Override
    public BasicDataDto.LawResponse createLaw(BasicDataDto.LawRequest request) {
        OhdpLaw entity = new OhdpLaw();
        applyLawRequest(entity, request);
        return toLawResponse(lawRepository.save(entity));
    }

    @Override
    public BasicDataDto.LawResponse updateLaw(Long id, BasicDataDto.LawRequest request) {
        OhdpLaw entity = lawRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BusinessException("法律法规不存在: " + id));
        applyLawRequest(entity, request);
        return toLawResponse(lawRepository.save(entity));
    }

    @Override
    public void deleteLaw(Long id) {
        OhdpLaw entity = lawRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BusinessException("法律法规不存在: " + id));
        entity.setDeleted(true);
        lawRepository.save(entity);
    }

    private void applyLawRequest(OhdpLaw entity, BasicDataDto.LawRequest request) {
        entity.setTitle(request.getTitle());
        entity.setDocNo(request.getDocNo());
        entity.setLevel(request.getLevel());
        entity.setPublishDate(request.getPublishDate());
        entity.setEffectiveDate(request.getEffectiveDate());
        entity.setPublisher(request.getPublisher());
        if (request.getIsValid() != null) entity.setIsValid(request.getIsValid());
        if (request.getIsOpen() != null) entity.setIsOpen(request.getIsOpen());
        entity.setFileId(request.getFileId());
        entity.setNotes(request.getNotes());
    }

    private BasicDataDto.LawResponse toLawResponse(OhdpLaw entity) {
        return BasicDataDto.LawResponse.builder()
                .id(entity.getId())
                .title(entity.getTitle())
                .docNo(entity.getDocNo())
                .level(entity.getLevel())
                .levelLabel(OhdpCodeLabels.label(entity.getLevel()))
                .publishDate(entity.getPublishDate())
                .effectiveDate(entity.getEffectiveDate())
                .publisher(entity.getPublisher())
                .isValid(entity.getIsValid())
                .isOpen(entity.getIsOpen())
                .fileId(entity.getFileId())
                .currentDocument(referenceDocumentService.latest(OhdpReferenceDocumentService.LAW, entity.getId()).orElse(null))
                .notes(entity.getNotes())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    // ==================== 标准规范 ====================

    @Override
    @Transactional(readOnly = true)
    public Page<BasicDataDto.StandardResponse> pageStandards(String keyword, String level, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
        boolean hasKeyword = StringUtils.hasText(keyword);
        boolean hasLevel = StringUtils.hasText(level);
        Page<OhdpStandard> result;
        if (hasKeyword && hasLevel) {
            result = standardRepository.findByStandardNameContainingIgnoreCaseAndLevelAndDeletedFalse(keyword, level, pageable);
        } else if (hasKeyword) {
            result = standardRepository.findByStandardNameContainingIgnoreCaseAndDeletedFalse(keyword, pageable);
        } else if (hasLevel) {
            result = standardRepository.findByLevelAndDeletedFalse(level, pageable);
        } else {
            result = standardRepository.findByDeletedFalse(pageable);
        }
        return result.map(this::toStandardResponse);
    }

    @Override
    public BasicDataDto.StandardResponse createStandard(BasicDataDto.StandardRequest request) {
        OhdpStandard entity = new OhdpStandard();
        applyStandardRequest(entity, request);
        return toStandardResponse(standardRepository.save(entity));
    }

    @Override
    public BasicDataDto.StandardResponse updateStandard(Long id, BasicDataDto.StandardRequest request) {
        OhdpStandard entity = standardRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BusinessException("标准规范不存在: " + id));
        applyStandardRequest(entity, request);
        return toStandardResponse(standardRepository.save(entity));
    }

    @Override
    public void deleteStandard(Long id) {
        OhdpStandard entity = standardRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BusinessException("标准规范不存在: " + id));
        entity.setDeleted(true);
        standardRepository.save(entity);
    }

    private void applyStandardRequest(OhdpStandard entity, BasicDataDto.StandardRequest request) {
        entity.setStandardName(request.getStandardName());
        entity.setStandardCode(request.getStandardCode());
        entity.setLevel(request.getLevel());
        entity.setUseScope(request.getUseScope());
        entity.setPublishDate(request.getPublishDate());
        if (request.getIsValid() != null) entity.setIsValid(request.getIsValid());
        if (request.getIsOpen() != null) entity.setIsOpen(request.getIsOpen());
        entity.setFileId(request.getFileId());
        entity.setNotes(request.getNotes());
    }

    private BasicDataDto.StandardResponse toStandardResponse(OhdpStandard entity) {
        return BasicDataDto.StandardResponse.builder()
                .id(entity.getId())
                .standardName(entity.getStandardName())
                .standardCode(entity.getStandardCode())
                .level(entity.getLevel())
                .levelLabel(OhdpCodeLabels.label(entity.getLevel()))
                .useScope(entity.getUseScope())
                .publishDate(entity.getPublishDate())
                .isValid(entity.getIsValid())
                .isOpen(entity.getIsOpen())
                .fileId(entity.getFileId())
                .currentDocument(referenceDocumentService.latest(OhdpReferenceDocumentService.STANDARD, entity.getId()).orElse(null))
                .notes(entity.getNotes())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    // ==================== 地区字典 ====================

    @Override
    @Transactional(readOnly = true)
    public List<BasicDataDto.RegionResponse> listRegions(Long parentId) {
        List<OhdpRegion> list;
        if (parentId != null) {
            list = regionRepository.findByParentIdAndDeletedFalseOrderBySortNoAsc(parentId);
        } else {
            list = regionRepository.findAllByDeletedFalseOrderByLevelAscSortNoAsc();
        }
        return list.stream().map(this::toRegionResponse).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public BasicDataDto.RegionResponse findRegionById(Long id) {
        OhdpRegion entity = regionRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BusinessException("地区不存在: " + id));
        return toRegionResponse(entity);
    }

    @Override
    public BasicDataDto.RegionResponse createRegion(BasicDataDto.RegionRequest request) {
        OhdpRegion entity = new OhdpRegion();
        applyRegionRequest(entity, request);
        return toRegionResponse(regionRepository.save(entity));
    }

    @Override
    public BasicDataDto.RegionResponse updateRegion(Long id, BasicDataDto.RegionRequest request) {
        OhdpRegion entity = regionRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BusinessException("地区不存在: " + id));
        applyRegionRequest(entity, request);
        return toRegionResponse(regionRepository.save(entity));
    }

    @Override
    public void deleteRegion(Long id) {
        OhdpRegion entity = regionRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BusinessException("地区不存在: " + id));
        entity.setDeleted(true);
        regionRepository.save(entity);
    }

    private void applyRegionRequest(OhdpRegion entity, BasicDataDto.RegionRequest request) {
        entity.setParentId(request.getParentId());
        entity.setRegionCode(request.getRegionCode());
        entity.setRegionName(request.getRegionName());
        entity.setLevel(request.getLevel());
        if (request.getSortNo() != null) entity.setSortNo(request.getSortNo());
    }

    private BasicDataDto.RegionResponse toRegionResponse(OhdpRegion entity) {
        return BasicDataDto.RegionResponse.builder()
                .id(entity.getId())
                .parentId(entity.getParentId())
                .regionCode(entity.getRegionCode())
                .regionName(entity.getRegionName())
                .level(entity.getLevel())
                .levelLabel(OhdpCodeLabels.regionLevel(entity.getLevel()))
                .sortNo(entity.getSortNo())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    // ==================== 设备管理 ====================

    @Override
    @Transactional(readOnly = true)
    public Page<BasicDataDto.DeviceResponse> pageDevices(String keyword, String deviceType, String status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
        boolean hasKeyword = StringUtils.hasText(keyword);
        boolean hasType = StringUtils.hasText(deviceType);
        boolean hasStatus = StringUtils.hasText(status);
        Page<OhdpDevice> result;
        if (hasKeyword && hasType && hasStatus) {
            result = deviceRepository.findByKeywordAndDeviceTypeAndStatusAndDeletedFalse(keyword, deviceType, status, pageable);
        } else if (hasKeyword && hasType) {
            result = deviceRepository.findByKeywordAndDeviceTypeAndDeletedFalse(keyword, deviceType, pageable);
        } else if (hasKeyword && hasStatus) {
            result = deviceRepository.findByKeywordAndStatusAndDeletedFalse(keyword, status, pageable);
        } else if (hasKeyword) {
            result = deviceRepository.findByKeywordAndDeletedFalse(keyword, pageable);
        } else if (hasType && hasStatus) {
            result = deviceRepository.findByDeviceTypeAndStatusAndDeletedFalse(deviceType, status, pageable);
        } else if (hasType) {
            result = deviceRepository.findByDeviceTypeAndDeletedFalse(deviceType, pageable);
        } else if (hasStatus) {
            result = deviceRepository.findByStatusAndDeletedFalse(status, pageable);
        } else {
            result = deviceRepository.findByDeletedFalse(pageable);
        }
        return result.map(this::toDeviceResponse);
    }

    @Override
    public BasicDataDto.DeviceResponse createDevice(BasicDataDto.DeviceRequest request) {
        OhdpDevice entity = new OhdpDevice();
        applyDeviceRequest(entity, request);
        return toDeviceResponse(deviceRepository.save(entity));
    }

    @Override
    public BasicDataDto.DeviceResponse updateDevice(Long id, BasicDataDto.DeviceRequest request) {
        OhdpDevice entity = deviceRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BusinessException("设备不存在: " + id));
        applyDeviceRequest(entity, request);
        return toDeviceResponse(deviceRepository.save(entity));
    }

    @Override
    public void deleteDevice(Long id) {
        OhdpDevice entity = deviceRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BusinessException("设备不存在: " + id));
        entity.setDeleted(true);
        deviceRepository.save(entity);
    }

    private void applyDeviceRequest(OhdpDevice entity, BasicDataDto.DeviceRequest request) {
        entity.setDeviceName(request.getDeviceName());
        entity.setDeviceType(request.getDeviceType());
        entity.setDeviceModel(request.getDeviceModel());
        entity.setDeviceNo(request.getDeviceNo());
        entity.setManufacturer(request.getManufacturer());
        entity.setCalibrationDate(request.getCalibrationDate());
        entity.setCalibrationExpiry(request.getCalibrationExpiry());
        entity.setCertificateNo(request.getCertificateNo());
        if (StringUtils.hasText(request.getStatus())) entity.setStatus(request.getStatus());
        entity.setNotes(request.getNotes());
    }

    private BasicDataDto.DeviceResponse toDeviceResponse(OhdpDevice entity) {
        return BasicDataDto.DeviceResponse.builder()
                .id(entity.getId())
                .deviceName(entity.getDeviceName())
                .deviceType(entity.getDeviceType())
                .deviceTypeLabel(OhdpCodeLabels.label(entity.getDeviceType()))
                .deviceModel(entity.getDeviceModel())
                .deviceNo(entity.getDeviceNo())
                .manufacturer(entity.getManufacturer())
                .calibrationDate(entity.getCalibrationDate())
                .calibrationExpiry(entity.getCalibrationExpiry()).certificateNo(entity.getCertificateNo())
                .status(entity.getStatus())
                .statusLabel(OhdpCodeLabels.label(entity.getStatus()))
                .notes(entity.getNotes())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
