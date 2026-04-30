package com.dsf.ohdp.entity;

import com.dsf.common.persistence.BaseAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * @author kevin
 * @description 检测方法配置
 */
@Getter
@Setter
@Entity
@Table(name = "ohdp_detection_method")
public class OhdpDetectionMethod extends BaseAuditEntity {

    @Column(name = "method_name", nullable = false, length = 200)
    private String methodName;

    @Column(name = "method_code", unique = true, length = 64)
    private String methodCode;

    @Column(name = "method_abbr", length = 32)
    private String methodAbbr;

    @Column(name = "formula", length = 500)
    private String formula;

    @Column(name = "formula_code", length = 64)
    private String formulaCode;

    @Column(name = "standard_code", length = 128)
    private String standardCode;

    @Column(name = "result_decimal_places")
    private Integer resultDecimalPlaces = 4;

    @Column(name = "calc_decimal_places")
    private Integer calcDecimalPlaces = 4;

    @Column(name = "enabled")
    private Boolean enabled = true;
}
