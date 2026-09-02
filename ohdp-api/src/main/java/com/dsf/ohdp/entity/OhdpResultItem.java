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

    @Column(name = "judgement", length = 20)
    private String judgement;
}
