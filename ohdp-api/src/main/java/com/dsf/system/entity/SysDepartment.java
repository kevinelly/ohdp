package com.dsf.system.entity;

import com.dsf.common.persistence.BaseAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "sys_department")
public class SysDepartment extends BaseAuditEntity {

    @Column(name = "parent_id")
    private Long parentId;

    @Column(name = "dept_name", nullable = false, length = 100)
    private String deptName;

    @Column(name = "dept_code", length = 64)
    private String deptCode;

    @Column(name = "sort_no")
    private Integer sortNo = 0;

    @Column(name = "leader_name", length = 64)
    private String leaderName;

    @Column(name = "contact_phone", length = 50)
    private String contactPhone;

    @Column(name = "enabled")
    private Boolean enabled = true;
}
