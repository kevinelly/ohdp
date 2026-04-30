package com.dsf.ohdp.entity;

import com.dsf.common.persistence.BaseAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * @author kevin
 * @description 危害因素主表
 */
@Getter
@Setter
@Entity
@Table(name = "ohdp_hazard_factor")
public class OhdpHazardFactor extends BaseAuditEntity {

    @Column(name = "factor_name", nullable = false, length = 200)
    private String factorName;

    @Column(name = "factor_code", length = 64)
    private String factorCode;

    /**
     * PHYSICAL/CHEMICAL/DUST/BIOLOGICAL/RADIATION/GENERAL/OTHER
     */
    @Column(name = "factor_category", nullable = false, length = 32)
    private String factorCategory;

    @Column(name = "cas_no", length = 64)
    private String casNo;

    @Column(name = "english_name", length = 200)
    private String englishName;

    @Column(name = "sort_no")
    private Integer sortNo = 0;

    @Column(name = "enabled")
    private Boolean enabled = true;
}
