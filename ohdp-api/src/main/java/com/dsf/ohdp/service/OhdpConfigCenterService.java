package com.dsf.ohdp.service;

import com.dsf.ohdp.dto.ConfigCenterDto;
import java.util.List;
import org.springframework.data.domain.Page;

public interface OhdpConfigCenterService {

    // ==================== 检测方法 ====================

    Page<ConfigCenterDto.DetectionMethodResponse> pageDetectionMethods(ConfigCenterDto.DetectionMethodQuery query);

    List<ConfigCenterDto.DetectionMethodResponse> listAllDetectionMethods();

    ConfigCenterDto.DetectionMethodResponse createDetectionMethod(ConfigCenterDto.DetectionMethodRequest request);

    ConfigCenterDto.DetectionMethodResponse updateDetectionMethod(Long id, ConfigCenterDto.DetectionMethodRequest request);

    void deleteDetectionMethod(Long id);

    // ==================== 机构配置 ====================

    ConfigCenterDto.OrgConfigResponse getOrgConfig();

    ConfigCenterDto.OrgConfigResponse saveOrgConfig(ConfigCenterDto.OrgConfigRequest request);

    // ==================== 检测分类 ====================

    List<ConfigCenterDto.DetectTypeResponse> listDetectTypes(Boolean enabled);

    ConfigCenterDto.DetectTypeResponse createDetectType(ConfigCenterDto.DetectTypeRequest request);

    ConfigCenterDto.DetectTypeResponse updateDetectType(Long id, ConfigCenterDto.DetectTypeRequest request);

    void deleteDetectType(Long id);

    // ==================== 报告模板目录 ====================

    List<ConfigCenterDto.ReportCatalogResponse> listReportCatalogs(Long detectTypeId);

    ConfigCenterDto.ReportCatalogResponse createReportCatalog(ConfigCenterDto.ReportCatalogRequest request);

    ConfigCenterDto.ReportCatalogResponse updateReportCatalog(Long id, ConfigCenterDto.ReportCatalogRequest request);

    void deleteReportCatalog(Long id);
}
