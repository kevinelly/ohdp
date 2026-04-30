package com.dsf.ohdp.entity;

import com.dsf.common.persistence.BaseAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * @author kevin
 * @description 危害因素检测方法关联
 */
@Getter
@Setter
@Entity
@Table(name = "ohdp_hazard_factor_method")
public class OhdpHazardFactorMethod extends BaseAuditEntity {

    @Column(name = "factor_id", nullable = false)
    private Long factorId;

    /**
     * 关联 ohdp_detection_method.id，逻辑关联，不强制外键
     */
    @Column(name = "method_id", nullable = false)
    private Long methodId;

    @Column(name = "is_default")
    private Boolean isDefault = false;
}
