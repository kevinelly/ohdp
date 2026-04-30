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
 * @description 实验室检测结果（含标准曲线回归参数）
 * @unit Unit 作用说明：
 *       1) unit 表示"最小业务单元"或"计量单位"；
 *       2) 在本模块中用于统一数据口径，避免前后端对同一字段理解不一致；
 *       3) 典型值示例：mg/m³（空气浓度）、μg/mL（溶液浓度）。
 */
@Getter
@Setter
@Entity
@Table(name = "ohdp_lab_result")
public class OhdpLabResult extends BaseAuditEntity {

    @Column(name = "task_id", nullable = false)
    private Long taskId;

    @Column(name = "sample_id")
    private Long sampleId;

    @Column(name = "factor_id")
    private Long factorId;

    @Column(name = "factor_name", nullable = false, length = 200)
    private String factorName;

    @Column(name = "method_id")
    private Long methodId;

    @Column(name = "method_name", length = 200)
    private String methodName;

    @Column(name = "intercept_a", precision = 20, scale = 8)
    private BigDecimal interceptA;

    @Column(name = "slope_b", precision = 20, scale = 8)
    private BigDecimal slopeB;

    @Column(name = "correlation_r", precision = 10, scale = 8)
    private BigDecimal correlationR;

    @Column(name = "desorption_volume", precision = 10, scale = 4)
    private BigDecimal desorptionVolume = BigDecimal.ONE;

    @Column(name = "desorption_efficiency", precision = 10, scale = 4)
    private BigDecimal desorptionEfficiency = BigDecimal.valueOf(100.0);

    @Column(name = "sampling_volume", precision = 10, scale = 4)
    private BigDecimal samplingVolume;

    @Column(name = "result_decimal_places")
    private Integer resultDecimalPlaces = 4;

    @Column(name = "calc_decimal_places")
    private Integer calcDecimalPlaces = 4;

    @Column(name = "result_value", precision = 16, scale = 6)
    private BigDecimal resultValue;

    @Column(name = "result_unit", length = 32)
    private String resultUnit;

    @Column(name = "min_detect_value", precision = 16, scale = 6)
    private BigDecimal minDetectValue;

    @Column(name = "min_quantify_value", precision = 16, scale = 6)
    private BigDecimal minQuantifyValue;

    @Column(name = "analyst_name", length = 64)
    private String analystName;

    @Column(name = "analysis_date")
    private LocalDate analysisDate;

    @Column(name = "notes", length = 500)
    private String notes;
}
