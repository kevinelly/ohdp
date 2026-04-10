package com.dsf.ohdp.entity;

import com.dsf.common.persistence.BaseAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * @author kevin
 * @description 岗位模板维护
 * @unit Unit 作用说明：
 *       1) unit 表示“最小业务单元”或“计量单位”；
 *       2) 在本模块中用于统一数据口径，避免前后端对同一字段理解不一致；
 *       3) 典型值示例：kg（重量）、box（箱）、item（件）。
 */
@Getter
@Setter
@Entity
@Table(name = "ohdp_position_template")
public class OhdpPositionTemplate extends BaseAuditEntity {

    @Column(name = "job_name", nullable = false, length = 100)
    private String jobName;

    @Column(name = "shift_system", length = 50)
    private String shiftSystem;

    @Column(name = "working_hours", length = 50)
    private String workingHours;

    @Column(name = "operation_type", length = 100)
    private String operationType;

    @Column(name = "operation_duration", length = 50)
    private String operationDuration;

    @Column(name = "hazards", length = 500)
    private String hazards;

    @Column(name = "ppe", length = 500)
    private String ppe;

    @Column(name = "protection_facilities", length = 500)
    private String protectionFacilities;

    @Column(name = "notes", length = 1000)
    private String notes;
}

