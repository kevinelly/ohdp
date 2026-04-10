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

@Getter
@Setter
@Entity
@Table(name = "sys_user", uniqueConstraints = {
        @UniqueConstraint(name = "uk_sys_user_username", columnNames = "username"),
        @UniqueConstraint(name = "uk_sys_user_employee_id", columnNames = "employee_id")
})
public class SysUser extends BaseAuditEntity {

    @Column(name = "username", nullable = false, length = 64)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "employee_id")
    private Long employeeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 16)
    private UserRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private RecordStatus status;

    @Column(name = "must_change_password", nullable = false)
    private Boolean mustChangePassword;
}
