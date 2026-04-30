package com.dsf.ohdp.entity;

import com.dsf.common.persistence.BaseAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * @author kevin
 * @description 机构配置（单例，id=1）
 */
@Getter
@Setter
@Entity
@Table(name = "ohdp_org_config")
public class OhdpOrgConfig extends BaseAuditEntity {

    @Column(name = "org_name", length = 200)
    private String orgName;

    @Column(name = "org_code", length = 64)
    private String orgCode;

    @Column(name = "org_address", length = 255)
    private String orgAddress;

    @Column(name = "contact_name", length = 64)
    private String contactName;

    @Column(name = "contact_phone", length = 50)
    private String contactPhone;

    @Column(name = "cert_no", length = 128)
    private String certNo;

    @Column(name = "cert_file_id")
    private Long certFileId;

    @Column(name = "sample_no_prefix", length = 20)
    private String sampleNoPrefix = "S";

    @Column(name = "sample_no_year_format", length = 10)
    private String sampleNoYearFormat = "YYYY";

    @Column(name = "controlled_no_prefix", length = 20)
    private String controlledNoPrefix = "KZ";

    @Column(name = "report_no_prefix", length = 20)
    private String reportNoPrefix = "R";

    @Column(name = "report_no_year_format", length = 10)
    private String reportNoYearFormat = "YYYY";
}
