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
 * @description 系统权限
 * @unit Unit 作用说明：
 *       1) unit 表示“最小业务单元”或“计量单位”；
 *       2) 在本模块中用于统一数据口径，避免前后端对同一字段理解不一致；
 *       3) 典型值示例：kg（重量）、box（箱）、item（件）。
 */
@Getter
@Setter
@Entity
@Table(name = "sys_permission", uniqueConstraints = {
        @UniqueConstraint(name = "uk_sys_permission_code", columnNames = "perm_code")
})
public class SysPermission extends BaseAuditEntity {

    @Column(name = "perm_code", nullable = false, length = 64)
    private String permCode;

    @Column(name = "perm_name", nullable = false, length = 128)
    private String permName;

    @Enumerated(EnumType.STRING)
    @Column(name = "perm_type", nullable = false, length = 16)
    private PermissionType permType;

    @Column(name = "api_method", length = 10)
    private String apiMethod;

    @Column(name = "api_path", length = 255)
    private String apiPath;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(name = "sort_no")
    private Integer sortNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private RecordStatus status;
}

