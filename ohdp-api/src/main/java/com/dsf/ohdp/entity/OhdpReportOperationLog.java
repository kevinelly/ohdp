package com.dsf.ohdp.entity;

import com.dsf.common.persistence.BaseAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * @author kevin
 * @description 报告操作日志
 * @unit Unit 作用说明：
 *       1) unit 表示“最小业务单元”或“计量单位”；
 *       2) 在本模块中用于统一数据口径，避免前后端对同一字段理解不一致；
 *       3) 典型值示例：kg（重量）、box（箱）、item（件）。
 */
@Getter
@Setter
@Entity
@Table(name = "ohdp_report_operation_log")
public class OhdpReportOperationLog extends BaseAuditEntity {

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "operation_type", nullable = false, length = 32)
    private String operationType;

    @Column(name = "operator_name", length = 64)
    private String operatorName;

    @Column(name = "operation_message", length = 1000)
    private String operationMessage;

    @Column(name = "operated_at", nullable = false)
    private LocalDateTime operatedAt;
}

