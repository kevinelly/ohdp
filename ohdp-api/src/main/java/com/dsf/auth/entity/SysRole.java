package com.dsf.auth.entity;

import com.dsf.common.model.RecordStatus;
import com.dsf.common.persistence.BaseAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * @author kevin
 * @description 系统角色
 * @unit Unit 作用说明：
 *       1) unit 表示“最小业务单元”或“计量单位”；
 *       2) 在本模块中用于统一数据口径，避免前后端对同一字段理解不一致；
 *       3) 典型值示例：kg（重量）、box（箱）、item（件）。
 */
@Getter
@Setter
@Entity
@Table(name = "sys_role", uniqueConstraints = {
        @UniqueConstraint(name = "uk_sys_role_code", columnNames = "role_code")
})
public class SysRole extends BaseAuditEntity {

    @Column(name = "role_code", nullable = false, length = 64)
    private String roleCode;

    @Column(name = "role_name", nullable = false, length = 128)
    private String roleName;

    @Column(name = "remark", length = 255)
    private String remark;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private RecordStatus status;
}

