package com.dsf.ohdp.entity;

import com.dsf.common.persistence.BaseAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/**
 * @author kevin
 * @description 项目检测结果
 * @unit Unit 作用说明：
 *       1) unit 表示“最小业务单元”或“计量单位”；
 *       2) 在本模块中用于统一数据口径，避免前后端对同一字段理解不一致；
 *       3) 典型值示例：kg（重量）、box（箱）、item（件）。
 */
@Getter
@Setter
@Entity
@Table(name = "ohdp_result_item")
public class OhdpResultItem extends BaseAuditEntity {

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "position_id")
    private Long positionId;

    @Column(name = "factor_id")
    private Long factorId;

    @Column(name = "job_name", nullable = false, length = 100)
    private String jobName;

    @Column(name = "factor_name", nullable = false, length = 120)
    private String factorName;

    @Column(name = "metric_type", nullable = false, length = 40)
    private String metricType;

    @Column(name = "value_raw", length = 80)
    private String valueRaw;

    @Column(name = "value_num", precision = 16, scale = 6)
    private BigDecimal valueNum;

    @Column(name = "unit", length = 32)
    private String unit;

    @Column(name = "limit_value", precision = 16, scale = 6)
    private BigDecimal limitValue;

    @Column(name = "limit_unit", length = 32)
    private String limitUnit;

    @Column(name = "measurement_date")
    private LocalDate measurementDate;

    @Column(name = "measurement_body_part", length = 80)
    private String measurementBodyPart;

    @Column(name = "measurement_band", length = 120)
    private String measurementBand;

    @Column(name = "daily_exposure_hours", precision = 8, scale = 2)
    private BigDecimal dailyExposureHours;

    @Column(name = "weekly_exposure_hours", precision = 8, scale = 2)
    private BigDecimal weeklyExposureHours;

    @Column(name = "reduction_factor", precision = 12, scale = 6)
    private BigDecimal reductionFactor;

    @Column(name = "adjusted_limit_value", precision = 16, scale = 6)
    private BigDecimal adjustedLimitValue;

    @Column(name = "judgement", length = 20)
    private String judgement;
    @Column(name = "factor_category", columnDefinition = "TEXT")
    private String factorCategory;

    @Column(name = "point_code", columnDefinition = "TEXT")
    private String pointCode;

    @Column(name = "sample_code", columnDefinition = "TEXT")
    private String sampleCode;

    @Column(name = "detection_mode", columnDefinition = "TEXT")
    private String detectionMode;

    @Column(name = "noise_type", columnDefinition = "TEXT")
    private String noiseType;

    @Column(name = "laboratory_date")
    private LocalDate laboratoryDate;

    @Column(name = "detection_basis", columnDefinition = "TEXT")
    private String detectionBasis;

    @Column(name = "detection_method", columnDefinition = "TEXT")
    private String detectionMethod;

    @Column(name = "instrument", columnDefinition = "TEXT")
    private String instrument;

    @Column(name = "testing_organization", columnDefinition = "TEXT")
    private String testingOrganization;

    @Column(name = "source_record_no", columnDefinition = "TEXT")
    private String sourceRecordNo;

    @Column(name = "detection_limit", columnDefinition = "TEXT")
    private String detectionLimit;

    @Column(name = "quantification_limit", columnDefinition = "TEXT")
    private String quantificationLimit;

    @Column(name = "evaluation_basis", columnDefinition = "TEXT")
    private String evaluationBasis;

    @Column(name = "over_limit_reason", columnDefinition = "TEXT")
    private String overLimitReason;

    @Column(name = "weekly_work_days")
    private Integer weeklyWorkDays;

    @Column(name = "reduction_applicable")
    private Boolean reductionApplicable;

    @Column(name = "peak_limit_multiplier", precision = 16, scale = 6)
    private BigDecimal peakLimitMultiplier;

    @Column(name = "peak_exposure_minutes", precision = 16, scale = 6)
    private BigDecimal peakExposureMinutes;

    @Column(name = "peak_times_per_shift")
    private Integer peakTimesPerShift;

    @Column(name = "peak_interval_minutes", precision = 16, scale = 6)
    private BigDecimal peakIntervalMinutes;

    @Column(name = "mixed_exposure_group", columnDefinition = "TEXT")
    private String mixedExposureGroup;

    @Column(name = "mixed_exposure_basis", columnDefinition = "TEXT")
    private String mixedExposureBasis;

    @Column(name = "measurement_conditions", columnDefinition = "TEXT")
    private String measurementConditions;

}
