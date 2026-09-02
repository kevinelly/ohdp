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
@Table(name = "ohdp_report_text_template")
public class OhdpReportTextTemplate extends BaseAuditEntity {

    @Column(name = "template_type", nullable = false, length = 32)
    private String templateType;

    @Column(name = "template_name", nullable = false, length = 128)
    private String templateName;

    @Column(name = "template_content", nullable = false, length = 6000)
    private String templateContent;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled;
}

