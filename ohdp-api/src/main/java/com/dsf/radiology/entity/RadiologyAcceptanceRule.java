package com.dsf.radiology.entity;

import com.dsf.common.persistence.BaseAuditEntity;
import com.dsf.radiology.model.RadiologyModality;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "qc_acceptance_rule", uniqueConstraints = @UniqueConstraint(
        name = "uk_qc_rule_modality_item_version", columnNames = {"modality", "item_code", "rule_version"}))
public class RadiologyAcceptanceRule extends BaseAuditEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "modality", nullable = false, length = 8)
    private RadiologyModality modality;

    @Column(name = "item_code", nullable = false, length = 32)
    private String itemCode;

    @Column(name = "item_name", nullable = false, length = 255)
    private String itemName;

    @Column(name = "rule_version", nullable = false, length = 50)
    private String ruleVersion;

    @Column(name = "lower_limit", precision = 20, scale = 8)
    private BigDecimal lowerLimit;

    @Column(name = "upper_limit", precision = 20, scale = 8)
    private BigDecimal upperLimit;

    @Column(name = "expected_text", length = 255)
    private String expectedText;

    @Column(name = "unit", length = 100)
    private String unit;

    @Column(name = "standard_code", nullable = false, length = 100)
    private String standardCode;

    @Column(name = "standard_name", nullable = false, length = 500)
    private String standardName;

    @Column(name = "detection_category", length = 64)
    private String detectionCategory;
    @Column(name = "lower_inclusive")
    private Boolean lowerInclusive = true;
    @Column(name = "upper_inclusive")
    private Boolean upperInclusive = true;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled = true;
}
