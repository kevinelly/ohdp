package com.dsf.radiology.entity;

import com.dsf.common.persistence.BaseAuditEntity;
import com.dsf.radiology.model.RadiologyFieldRole;
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
@Table(name = "qc_measurement", uniqueConstraints = @UniqueConstraint(
        name = "uk_qc_measurement_case_item_field", columnNames = {"case_id", "item_code", "field_code"}))
public class RadiologyMeasurement extends BaseAuditEntity {

    @Column(name = "case_id", nullable = false)
    private Long caseId;

    @Column(name = "item_code", nullable = false, length = 32)
    private String itemCode;

    @Column(name = "field_code", nullable = false, length = 100)
    private String fieldCode;

    @Column(name = "field_label", nullable = false, length = 255)
    private String fieldLabel;

    @Enumerated(EnumType.STRING)
    @Column(name = "field_role", nullable = false, length = 20)
    private RadiologyFieldRole fieldRole;

    @Column(name = "value_num", precision = 38, scale = 16)
    private BigDecimal valueNum;

    @Column(name = "value_text", columnDefinition = "longtext")
    private String valueText;

    @Column(name = "unit", length = 100)
    private String unit;

    @Column(name = "sort_no", nullable = false)
    private Integer sortNo = 0;

    @Column(name = "display_scale")
    private Integer displayScale;
    @Column(name = "value_source", nullable = false, length = 20)
    private String valueSource = "MANUAL";
}
