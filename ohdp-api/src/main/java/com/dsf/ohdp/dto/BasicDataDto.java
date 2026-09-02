package com.dsf.ohdp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author kevin
 * @description 基础数据模块 DTO 定义（法律法规、标准规范、地区字典、设备管理）
 */
public final class BasicDataDto {

    private BasicDataDto() {
    }

    // ==================== 法律法规 ====================

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LawRequest {
        private String title;
        private String docNo;
        private String level;
        private LocalDate publishDate;
        private LocalDate effectiveDate;
        private String publisher;
        private Boolean isValid;
        private Boolean isOpen;
        private Long fileId;
        private String notes;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LawResponse {
        private Long id;
        private String title;
        private String docNo;
        private String level;
        private String levelLabel;
        private LocalDate publishDate;
        private LocalDate effectiveDate;
        private String publisher;
        private Boolean isValid;
        private Boolean isOpen;
        private Long fileId;
        private ReferenceDocumentResponse currentDocument;
        private String notes;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    // ==================== 标准规范 ====================

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StandardRequest {
        @NotBlank
        private String standardName;
        private String standardCode;
        private String level;
        private String useScope;
        private LocalDate publishDate;
        private Boolean isValid;
        private Boolean isOpen;
        private Long fileId;
        private String notes;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StandardResponse {
        private Long id;
        private String standardName;
        private String standardCode;
        private String level;
        private String levelLabel;
        private String useScope;
        private LocalDate publishDate;
        private Boolean isValid;
        private Boolean isOpen;
        private Long fileId;
        private ReferenceDocumentResponse currentDocument;
        private String notes;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    // ==================== 地区字典 ====================

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RegionRequest {
        private Long parentId;
        @NotBlank
        private String regionCode;
        @NotBlank
        private String regionName;
        @NotNull
        private Integer level;
        private Integer sortNo;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RegionResponse {
        private Long id;
        private Long parentId;
        private String regionCode;
        private String regionName;
        private Integer level;
        private String levelLabel;
        private Integer sortNo;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    // ==================== 设备管理 ====================

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeviceRequest {
        @NotBlank
        private String deviceName;
        @NotBlank
        private String deviceType;
        private String deviceModel;
        private String deviceNo;
        private String manufacturer;
        private LocalDate calibrationDate;
        private LocalDate calibrationExpiry;
        private String status;
        private String notes;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeviceResponse {
        private Long id;
        private String deviceName;
        private String deviceType;
        private String deviceTypeLabel;
        private String deviceModel;
        private String deviceNo;
        private String manufacturer;
        private LocalDate calibrationDate;
        private LocalDate calibrationExpiry;
        private String status;
        private String statusLabel;
        private String notes;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReferenceDocumentResponse {
        private Long id;
        private String referenceType;
        private Long referenceId;
        private Integer versionNo;
        private String versionLabel;
        private String originalName;
        private String contentType;
        private Long fileSize;
        private String sha256;
        private String sourceUrl;
        private String uploadedBy;
        private LocalDateTime uploadedAt;
        private Boolean current;
    }
}
