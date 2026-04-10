package com.dsf.ohdp.entity;

import com.dsf.common.persistence.BaseAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * @author kevin
 * @description 单位信息维护
 * @unit Unit 作用说明：
 *       1) unit 表示“最小业务单元”或“计量单位”；
 *       2) 在本模块中用于统一数据口径，避免前后端对同一字段理解不一致；
 *       3) 典型值示例：kg（重量）、box（箱）、item（件）。
 */
@Getter
@Setter
@Entity
@Table(name = "ohdp_company_profile", uniqueConstraints = {
        @UniqueConstraint(name = "uk_ohdp_company_name", columnNames = "company_name")
})
public class OhdpCompanyProfile extends BaseAuditEntity {

    @Column(name = "company_name", nullable = false, length = 200)
    private String companyName;

    @Column(name = "company_address", nullable = false, length = 255)
    private String companyAddress;

    @Column(name = "contact_name", length = 100)
    private String contactName;

    @Column(name = "contact_phone", length = 50)
    private String contactPhone;

    @Column(name = "industry_type", length = 100)
    private String industryType;

    @Column(name = "notes", length = 1000)
    private String notes;
}

