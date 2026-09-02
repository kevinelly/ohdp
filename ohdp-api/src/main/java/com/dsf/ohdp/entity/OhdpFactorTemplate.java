package com.dsf.ohdp.entity;

import com.dsf.common.persistence.BaseAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * @author kevin
 * @description 危害因素模板维护
 * @unit Unit 作用说明：
 *       1) unit 表示“最小业务单元”或“计量单位”；
 *       2) 在本模块中用于统一数据口径，避免前后端对同一字段理解不一致；
 *       3) 典型值示例：kg（重量）、box（箱）、item（件）。
 */
@Getter
@Setter
@Entity
@Table(name = "ohdp_factor_template")
public class OhdpFactorTemplate extends BaseAuditEntity {

    @Column(name = "factor_name", nullable = false, length = 120)
    private String factorName;

    @Column(name = "metric_type", nullable = false, length = 40)
    private String metricType;

    @Column(name = "default_unit", length = 32)
    private String defaultUnit;

    @Column(name = "default_limit_value", length = 32)
    private String defaultLimitValue;

    @Column(name = "default_limit_unit", length = 32)
    private String defaultLimitUnit;

    @Column(name = "detection_basis", length = 1000)
    private String detectionBasis;

    @Column(name = "detection_method", length = 500)
    private String detectionMethod;

    @Column(name = "detection_instruments", length = 1000)
    private String detectionInstruments;

    @Column(name = "notes", length = 1000)
    private String notes;
}

