package com.dsf.ohdp.entity;

import com.dsf.common.persistence.BaseAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/**
 * @author kevin
 * @description 标准曲线数据点（用于线性回归）
 * @unit Unit 作用说明：
 *       1) unit 表示"最小业务单元"或"计量单位"；
 *       2) 在本模块中用于统一数据口径，避免前后端对同一字段理解不一致；
 *       3) 典型值示例：μg/mL（浓度）、AU（吸光度）。
 */
@Getter
@Setter
@Entity
@Table(name = "ohdp_lab_curve_point")
public class OhdpLabCurvePoint extends BaseAuditEntity {

    @Column(name = "lab_result_id", nullable = false)
    private Long labResultId;

    @Column(name = "point_index", nullable = false)
    private Integer pointIndex;

    @Column(name = "concentration", precision = 16, scale = 6)
    private BigDecimal concentration;

    @Column(name = "absorbance", precision = 16, scale = 6)
    private BigDecimal absorbance;

    @Column(name = "absorbance_raw", precision = 16, scale = 6)
    private BigDecimal absorbanceRaw;

    @Column(name = "is_excluded")
    private Boolean isExcluded = false;
}
