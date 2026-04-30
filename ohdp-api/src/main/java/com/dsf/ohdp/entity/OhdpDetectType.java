package com.dsf.ohdp.entity;

import com.dsf.common.persistence.BaseAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * @author kevin
 * @description 项目检测分类
 */
@Getter
@Setter
@Entity
@Table(name = "ohdp_detect_type")
public class OhdpDetectType extends BaseAuditEntity {

    @Column(name = "type_name", nullable = false, length = 200)
    private String typeName;

    @Column(name = "type_code", nullable = false, unique = true, length = 64)
    private String typeCode;

    @Column(name = "type_abbr", length = 32)
    private String typeAbbr;

    @Column(name = "sort_no")
    private Integer sortNo = 0;

    @Column(name = "enabled")
    private Boolean enabled = true;
}
