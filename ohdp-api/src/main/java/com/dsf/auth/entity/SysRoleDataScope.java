package com.dsf.auth.entity;

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
 * @description 角色数据权限
 * @unit Unit 作用说明：
 *       1) unit 表示“最小业务单元”或“计量单位”；
 *       2) 在本模块中用于统一数据口径，避免前后端对同一字段理解不一致；
 *       3) 典型值示例：kg（重量）、box（箱）、item（件）。
 */
@Getter
@Setter
@Entity
@Table(name = "sys_role_data_scope", uniqueConstraints = {
        @UniqueConstraint(name = "uk_sys_role_data_scope_role", columnNames = "role_id")
})
public class SysRoleDataScope extends BaseAuditEntity {

    @Column(name = "role_id", nullable = false)
    private Long roleId;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope_type", nullable = false, length = 32)
    private DataScopeType scopeType;

    @Column(name = "store_ids_json", length = 2000)
    private String storeIdsJson;
}

