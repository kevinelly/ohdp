package com.dsf.ohdp.entity;

import com.dsf.common.persistence.BaseAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "ohdp_region")
public class OhdpRegion extends BaseAuditEntity {

    @Column(name = "parent_id")
    private Long parentId;

    @Column(name = "region_code", nullable = false, unique = true, length = 32)
    private String regionCode;

    @Column(name = "region_name", nullable = false, length = 100)
    private String regionName;

    /** 1=省 2=市 3=区县 */
    @Column(name = "level", nullable = false)
    private Integer level;

    @Column(name = "sort_no")
    private Integer sortNo = 0;
}
