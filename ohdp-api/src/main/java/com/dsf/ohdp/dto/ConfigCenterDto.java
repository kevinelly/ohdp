package com.dsf.ohdp.dto;

import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author kevin
 * @description 配置中心模块 DTO 定义
 */
public final class ConfigCenterDto {

    private ConfigCenterDto() {
    }

    // ==================== 检测方法 ====================

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DetectionMethodRequest {
        private String methodName;
        private String methodCode;
        private String methodAbbr;
        private String formula;
        private String formulaCode;
        private String standardCode;
        private Integer resultDecimalPlaces;
        private Integer calcDecimalPlaces;
        private Boolean enabled;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DetectionMethodResponse {
        private Long id;
        private String methodName;
        private String methodCode;
        private String methodAbbr;
        private String formula;
        private String formulaCode;
        private String standardCode;
        private Integer resultDecimalPlaces;
        private Integer calcDecimalPlaces;
        private Boolean enabled;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data
    public static class DetectionMethodQuery {
        private String keyword;
        private Boolean enabled;
        private int page = 0;
        private int size = 10;
    }

    // ==================== 机构配置 ====================

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrgConfigRequest {
        private String orgName;
        private String orgCode;
        private String orgAddress;
        private String contactName;
        private String contactPhone;
        private String certNo;
        private Long certFileId;
        private String sampleNoPrefix;
        private String sampleNoYearFormat;
        private String controlledNoPrefix;
        private String reportNoPrefix;
        private String reportNoYearFormat;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrgConfigResponse {
        private Long id;
        private String orgName;
        private String orgCode;
        private String orgAddress;
        private String contactName;
        private String contactPhone;
        private String certNo;
        private Long certFileId;
        private String sampleNoPrefix;
        private String sampleNoYearFormat;
        private String controlledNoPrefix;
        private String reportNoPrefix;
        private String reportNoYearFormat;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    // ==================== 检测分类 ====================

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DetectTypeRequest {
        private String typeName;
        private String typeCode;
        private String typeAbbr;
        private Integer sortNo;
        private Boolean enabled;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DetectTypeResponse {
        private Long id;
        private String typeName;
        private String typeCode;
        private String typeAbbr;
        private Integer sortNo;
        private Boolean enabled;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    // ==================== 报告模板目录 ====================

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReportCatalogRequest {
        private Long detectTypeId;
        private String catalogName;
        private String catalogCode;
        private Long parentId;
        private Integer sortNo;
        private Boolean isRequired;
        private String notes;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReportCatalogResponse {
        private Long id;
        private Long detectTypeId;
        private String catalogName;
        private String catalogCode;
        private Long parentId;
        private Integer sortNo;
        private Boolean isRequired;
        private String notes;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }
}
