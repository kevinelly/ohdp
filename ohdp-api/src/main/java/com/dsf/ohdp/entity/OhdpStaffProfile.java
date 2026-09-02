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
@Table(name = "ohdp_staff_profile")
public class OhdpStaffProfile extends BaseAuditEntity {

    @Column(name = "staff_name", nullable = false, length = 64)
    private String staffName;

    @Column(name = "staff_title", length = 64)
    private String staffTitle;

    @Column(name = "cert_no", nullable = false, length = 128)
    private String certNo;

    @Column(name = "contact_phone", length = 50)
    private String contactPhone;

    @Column(name = "notes", length = 1000)
    private String notes;
}

