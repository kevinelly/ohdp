package com.dsf.radiology.entity;

import com.dsf.common.persistence.BaseAuditEntity;
import com.dsf.radiology.model.RadiologyJudgement;
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
@Table(name = "qc_protection_point", uniqueConstraints = @UniqueConstraint(
        name = "uk_qc_protection_case_code", columnNames = {"case_id", "point_code"}))
public class RadiologyProtectionPoint extends BaseAuditEntity {

    @Column(name = "case_id", nullable = false)
    private Long caseId;

    @Column(name = "point_code", nullable = false, length = 50)
    private String pointCode;

    @Column(name = "point_name", nullable = false, length = 255)
    private String pointName;

    @Column(name = "location_description", length = 500)
    private String locationDescription;

    @Column(name = "readings_json", nullable = false, columnDefinition = "TEXT")
    private String readingsJson = "[]";

    @Column(name = "average_value", precision = 20, scale = 8)
    private BigDecimal averageValue;

    @Column(name = "result_value", precision = 20, scale = 8)
    private BigDecimal resultValue;

    @Column(name = "control_limit", precision = 20, scale = 8)
    private BigDecimal controlLimit;

    @Column(name = "annual_workload_mas", precision = 20, scale = 4)
    private BigDecimal annualWorkloadMas;

    @Column(name = "occupancy_factor", precision = 12, scale = 8)
    private BigDecimal occupancyFactor;

    @Column(name = "use_factor", precision = 12, scale = 8)
    private BigDecimal useFactor;

    @Column(name = "annual_effective_dose", precision = 20, scale = 8)
    private BigDecimal annualEffectiveDose;

    @Column(name = "annual_dose_limit", precision = 20, scale = 8)
    private BigDecimal annualDoseLimit;

    @Enumerated(EnumType.STRING)
    @Column(name = "judgement", nullable = false, length = 32)
    private RadiologyJudgement judgement = RadiologyJudgement.INCOMPLETE;

    @Column(name = "sort_no", nullable = false)
    private Integer sortNo = 0;
}
