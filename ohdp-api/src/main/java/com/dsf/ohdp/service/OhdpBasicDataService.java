package com.dsf.ohdp.service;

import com.dsf.ohdp.dto.BasicDataDto;
import java.util.List;
import org.springframework.data.domain.Page;

public interface OhdpBasicDataService {

    // ==================== 法律法规 ====================

    Page<BasicDataDto.LawResponse> pageLaws(String keyword, String level, int page, int size);

    BasicDataDto.LawResponse createLaw(BasicDataDto.LawRequest request);

    BasicDataDto.LawResponse updateLaw(Long id, BasicDataDto.LawRequest request);

    void deleteLaw(Long id);

    // ==================== 标准规范 ====================

    Page<BasicDataDto.StandardResponse> pageStandards(String keyword, String level, int page, int size);

    BasicDataDto.StandardResponse createStandard(BasicDataDto.StandardRequest request);

    BasicDataDto.StandardResponse updateStandard(Long id, BasicDataDto.StandardRequest request);

    void deleteStandard(Long id);

    // ==================== 地区字典 ====================

    List<BasicDataDto.RegionResponse> listRegions(Long parentId);

    BasicDataDto.RegionResponse findRegionById(Long id);

    BasicDataDto.RegionResponse createRegion(BasicDataDto.RegionRequest request);

    BasicDataDto.RegionResponse updateRegion(Long id, BasicDataDto.RegionRequest request);

    void deleteRegion(Long id);

    // ==================== 设备管理 ====================

    Page<BasicDataDto.DeviceResponse> pageDevices(String keyword, String deviceType, String status, int page, int size);

    BasicDataDto.DeviceResponse createDevice(BasicDataDto.DeviceRequest request);

    BasicDataDto.DeviceResponse updateDevice(Long id, BasicDataDto.DeviceRequest request);

    void deleteDevice(Long id);
}
