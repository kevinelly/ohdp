package com.dsf.ohdp.entity;

import com.dsf.common.persistence.BaseAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * @author kevin
 * @description 报告模板目录
 */
@Getter
@Setter
@Entity
@Table(name = "ohdp_report_template_catalog")
public class OhdpReportTemplateCatalog extends BaseAuditEntity {

    @Column(name = "detect_type_id")
    private Long detectTypeId;

    @Column(name = "catalog_name", nullable = false, length = 200)
    private String catalogName;

    @Column(name = "catalog_code", length = 64)
    private String catalogCode;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(name = "sort_no")
    private Integer sortNo = 0;

    @Column(name = "is_required")
    private Boolean isRequired = false;

    @Column(name = "notes", length = 500)
    private String notes;
}
