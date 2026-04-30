package com.dsf.ohdp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author kevin
 * @description 实验室检测结果模块 DTO 定义（含标准曲线与浓度计算）
 * @unit Unit 作用说明：
 *       1) unit 表示"最小业务单元"或"计量单位"；
 *       2) 在本模块中用于统一数据口径，避免前后端对同一字段理解不一致；
 *       3) 典型值示例：mg/m³（空气浓度）、μg/mL（溶液浓度）。
 */
public final class LabResultDto {

    private LabResultDto() {
    }

    // ─────────────────────────── 曲线数据点 ───────────────────────────

    @Data
    public static class CurvePointRequest {
        @NotNull
        private Integer pointIndex;
        private BigDecimal concentration;
        private BigDecimal absorbance;
        private BigDecimal absorbanceRaw;
        private Boolean isExcluded = false;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CurvePointResponse {
        private Long id;
        private Long labResultId;
        private Integer pointIndex;
        private BigDecimal concentration;
        private BigDecimal absorbance;
        private BigDecimal absorbanceRaw;
        private Boolean isExcluded;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data
    public static class CurvePointUpdateRequest {
        private Integer pointIndex;
        private BigDecimal concentration;
        private BigDecimal absorbance;
        private BigDecimal absorbanceRaw;
        private Boolean isExcluded;
    }

    // ─────────────────────────── 检测结果 ───────────────────────────

    @Data
    public static class LabResultRequest {
        @NotNull
        private Long taskId;

        private Long sampleId;
        private Long factorId;

        @NotBlank
        @Size(max = 200)
        private String factorName;

        private Long methodId;

        @Size(max = 200)
        private String methodName;

        private BigDecimal desorptionVolume;
        private BigDecimal desorptionEfficiency;
        private BigDecimal samplingVolume;
        private Integer resultDecimalPlaces;
        private Integer calcDecimalPlaces;

        @Size(max = 32)
        private String resultUnit;

        private BigDecimal minDetectValue;
        private BigDecimal minQuantifyValue;

        @Size(max = 64)
        private String analystName;

        private LocalDate analysisDate;

        @Size(max = 500)
        private String notes;

        /** 可选：创建时同时保存曲线数据点 */
        private List<CurvePointRequest> curvePoints;
    }

    @Data
    public static class LabResultUpdateRequest {
        private Long sampleId;
        private Long factorId;

        @Size(max = 200)
        private String factorName;

        private Long methodId;

        @Size(max = 200)
        private String methodName;

        private BigDecimal desorptionVolume;
        private BigDecimal desorptionEfficiency;
        private BigDecimal samplingVolume;
        private Integer resultDecimalPlaces;
        private Integer calcDecimalPlaces;

        @Size(max = 32)
        private String resultUnit;

        private BigDecimal minDetectValue;
        private BigDecimal minQuantifyValue;

        @Size(max = 64)
        private String analystName;

        private LocalDate analysisDate;

        @Size(max = 500)
        private String notes;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LabResultResponse {
        private Long id;
        private Long taskId;
        private Long sampleId;
        private Long factorId;
        private String factorName;
        private Long methodId;
        private String methodName;
        private BigDecimal interceptA;
        private BigDecimal slopeB;
        private BigDecimal correlationR;
        private BigDecimal desorptionVolume;
        private BigDecimal desorptionEfficiency;
        private BigDecimal samplingVolume;
        private Integer resultDecimalPlaces;
        private Integer calcDecimalPlaces;
        private BigDecimal resultValue;
        private String resultUnit;
        private BigDecimal minDetectValue;
        private BigDecimal minQuantifyValue;
        private String analystName;
        private LocalDate analysisDate;
        private String notes;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        /** 单个查询时附带曲线数据点 */
        private List<CurvePointResponse> curvePoints;
    }

    // ─────────────────────────── 回归计算响应 ───────────────────────────

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CalcResponse {
        private Long labResultId;
        private BigDecimal interceptA;
        private BigDecimal slopeB;
        private BigDecimal correlationR;
        private List<CurvePointResponse> curvePoints;
    }

    // ─────────────────────────── 样品浓度计算 ───────────────────────────

    @Data
    public static class CalcSampleRequest {
        @NotNull
        private BigDecimal absorbance;
        @NotNull
        private BigDecimal samplingVolume;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CalcSampleResponse {
        private Long labResultId;
        /** 由曲线求得的浓度 x（μg/mL） */
        private BigDecimal concentrationX;
        /** 样品中浓度 C_sample（μg/mL，考虑解吸体积与效率） */
        private BigDecimal concentrationSample;
        /** 空气中浓度 C_air（mg/m³） */
        private BigDecimal concentrationAir;
        /** 保存到检测结果的最终值 */
        private BigDecimal resultValue;
        private String resultUnit;
    }
}
