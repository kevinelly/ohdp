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
@Table(name = "sys_post")
public class SysPost extends BaseAuditEntity {

    @Column(name = "post_name", nullable = false, length = 100)
    private String postName;

    @Column(name = "post_code", length = 64)
    private String postCode;

    @Column(name = "sort_no")
    private Integer sortNo = 0;

    @Column(name = "enabled")
    private Boolean enabled = true;

    @Column(name = "notes", length = 500)
    private String notes;
}
