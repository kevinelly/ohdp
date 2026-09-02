package com.dsf.ohdp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author kevin
 * @description 危害因素管理模块 DTO 定义
 */
public final class HazardFactorDto {

    private HazardFactorDto() {
    }

    // -------------------------------------------------------------------------
    // HazardFactor
    // -------------------------------------------------------------------------

    @Data
    public static class HazardFactorRequest {

        @NotBlank
        private String factorName;

        private String factorCode;

        @NotBlank
        private String factorCategory;

        private String casNo;
        private String englishName;
        private Integer sortNo;
        private Boolean enabled;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HazardFactorResponse {
        private Long id;
        private String factorName;
        private String factorCode;
        private String factorCategory;
        private String factorCategoryLabel;
        private String casNo;
        private String englishName;
        private Integer sortNo;
        private Boolean enabled;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HazardFactorDetailResponse {
        private Long id;
        private String factorName;
        private String factorCode;
        private String factorCategory;
        private String factorCategoryLabel;
        private String casNo;
        private String englishName;
        private Integer sortNo;
        private Boolean enabled;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        private List<HazardFactorLimitResponse> limits;
    }

    // -------------------------------------------------------------------------
    // HazardFactorLimit
    // -------------------------------------------------------------------------

    @Data
    public static class HazardFactorLimitRequest {

        @NotBlank
        private String limitType;

        private BigDecimal limitValue;
        private String limitUnit;
        private String standardCode;
        private String notes;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HazardFactorLimitResponse {
        private Long id;
        private Long factorId;
        private String limitType;
        private String limitTypeLabel;
        private BigDecimal limitValue;
        private String limitUnit;
        private String standardCode;
        private String notes;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    // -------------------------------------------------------------------------
    // HazardFactorMethod
    // -------------------------------------------------------------------------

    @Data
    public static class HazardFactorMethodRequest {

        @NotNull
        private Long methodId;

        private Boolean isDefault;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HazardFactorMethodResponse {
        private Long id;
        private Long factorId;
        private Long methodId;
        private Boolean isDefault;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    // -------------------------------------------------------------------------
    // Sds
    // -------------------------------------------------------------------------

    @Data
    public static class SdsRequest {

        private Long factorId;

        @NotBlank
        private String substanceName;

        private String casNo;
        private String version;
        private Long fileId;
        private String notes;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SdsResponse {
        private Long id;
        private Long factorId;
        private String substanceName;
        private String casNo;
        private String version;
        private Long fileId;
        private String notes;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }
}
