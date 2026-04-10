package com.dsf.ohdp.entity;

import com.dsf.common.persistence.BaseAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/**
 * @author kevin
 * @description 职业危害项目主表
 * @unit Unit 作用说明：
 *       1) unit 表示“最小业务单元”或“计量单位”；
 *       2) 在本模块中用于统一数据口径，避免前后端对同一字段理解不一致；
 *       3) 典型值示例：kg（重量）、box（箱）、item（件）。
 */
@Getter
@Setter
@Entity
@Table(name = "ohdp_project", uniqueConstraints = {
        @UniqueConstraint(name = "uk_ohdp_project_code", columnNames = "project_code")
})
public class OhdpProject extends BaseAuditEntity {

    @Column(name = "project_code", nullable = false, length = 64)
    private String projectCode;

    @Column(name = "company_name", nullable = false, length = 200)
    private String companyName;

    @Column(name = "company_address", nullable = false, length = 255)
    private String companyAddress;

    @Column(name = "detect_type", nullable = false, length = 64)
    private String detectType;

    @Column(name = "sampling_date")
    private LocalDate samplingDate;

    @Column(name = "report_date")
    private LocalDate reportDate;

    @Column(name = "notes", length = 1000)
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ProjectStatus status;
}
