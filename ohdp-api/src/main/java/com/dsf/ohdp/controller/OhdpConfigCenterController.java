package com.dsf.ohdp.controller;

import com.dsf.ohdp.dto.ConfigCenterDto;
import com.dsf.ohdp.service.OhdpConfigCenterService;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "OHDP-Config")
@RestController
@RequestMapping("/api/ohdp/config")
@RequiredArgsConstructor
public class OhdpConfigCenterController {

    private final OhdpConfigCenterService configCenterService;

    // ==================== 检测方法 ====================

    @GetMapping("/detection-methods")
    public Page<ConfigCenterDto.DetectionMethodResponse> pageDetectionMethods(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        ConfigCenterDto.DetectionMethodQuery query = new ConfigCenterDto.DetectionMethodQuery();
        query.setKeyword(keyword);
        query.setEnabled(enabled);
        query.setPage(page);
        query.setSize(size);
        return configCenterService.pageDetectionMethods(query);
    }

    @GetMapping("/detection-methods/all")
    public List<ConfigCenterDto.DetectionMethodResponse> listAllDetectionMethods() {
        return configCenterService.listAllDetectionMethods();
    }

    @PostMapping("/detection-methods")
    public ConfigCenterDto.DetectionMethodResponse createDetectionMethod(
            @RequestBody ConfigCenterDto.DetectionMethodRequest request) {
        return configCenterService.createDetectionMethod(request);
    }

    @PutMapping("/detection-methods/{id}")
    public ConfigCenterDto.DetectionMethodResponse updateDetectionMethod(
            @PathVariable Long id,
            @RequestBody ConfigCenterDto.DetectionMethodRequest request) {
        return configCenterService.updateDetectionMethod(id, request);
    }

    @DeleteMapping("/detection-methods/{id}")
    public void deleteDetectionMethod(@PathVariable Long id) {
        configCenterService.deleteDetectionMethod(id);
    }

    // ==================== 机构配置 ====================

    @GetMapping("/org")
    public ConfigCenterDto.OrgConfigResponse getOrgConfig() {
        return configCenterService.getOrgConfig();
    }

    @PutMapping("/org")
    public ConfigCenterDto.OrgConfigResponse saveOrgConfig(
            @RequestBody ConfigCenterDto.OrgConfigRequest request) {
        return configCenterService.saveOrgConfig(request);
    }

    // ==================== 检测分类 ====================

    @GetMapping("/detect-types")
    public List<ConfigCenterDto.DetectTypeResponse> listDetectTypes(
            @RequestParam(required = false) Boolean enabled) {
        return configCenterService.listDetectTypes(enabled);
    }

    @PostMapping("/detect-types")
    public ConfigCenterDto.DetectTypeResponse createDetectType(
            @RequestBody ConfigCenterDto.DetectTypeRequest request) {
        return configCenterService.createDetectType(request);
    }

    @PutMapping("/detect-types/{id}")
    public ConfigCenterDto.DetectTypeResponse updateDetectType(
            @PathVariable Long id,
            @RequestBody ConfigCenterDto.DetectTypeRequest request) {
        return configCenterService.updateDetectType(id, request);
    }

    @DeleteMapping("/detect-types/{id}")
    public void deleteDetectType(@PathVariable Long id) {
        configCenterService.deleteDetectType(id);
    }

    // ==================== 报告模板目录 ====================

    @GetMapping("/report-catalogs")
    public List<ConfigCenterDto.ReportCatalogResponse> listReportCatalogs(
            @RequestParam(required = false) Long detectTypeId) {
        return configCenterService.listReportCatalogs(detectTypeId);
    }

    @PostMapping("/report-catalogs")
    public ConfigCenterDto.ReportCatalogResponse createReportCatalog(
            @RequestBody ConfigCenterDto.ReportCatalogRequest request) {
        return configCenterService.createReportCatalog(request);
    }

    @PutMapping("/report-catalogs/{id}")
    public ConfigCenterDto.ReportCatalogResponse updateReportCatalog(
            @PathVariable Long id,
            @RequestBody ConfigCenterDto.ReportCatalogRequest request) {
        return configCenterService.updateReportCatalog(id, request);
    }

    @DeleteMapping("/report-catalogs/{id}")
    public void deleteReportCatalog(@PathVariable Long id) {
        configCenterService.deleteReportCatalog(id);
    }
}
