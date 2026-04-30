package com.dsf.ohdp.entity;

import com.dsf.common.persistence.BaseAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/**
 * @author kevin
 * @description 危害因素限值明细
 */
@Getter
@Setter
@Entity
@Table(name = "ohdp_hazard_factor_limit")
public class OhdpHazardFactorLimit extends BaseAuditEntity {

    @Column(name = "factor_id", nullable = false)
    private Long factorId;

    /**
     * PC_TWA/PC_STEL/MAC/OEL/MEL/OTHER
     */
    @Column(name = "limit_type", nullable = false, length = 32)
    private String limitType;

    @Column(name = "limit_value", precision = 16, scale = 6)
    private BigDecimal limitValue;

    @Column(name = "limit_unit", length = 32)
    private String limitUnit;

    @Column(name = "standard_code", length = 128)
    private String standardCode;

    @Column(name = "notes", length = 500)
    private String notes;
}
