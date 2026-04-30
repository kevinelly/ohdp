package com.dsf.ohdp.entity;

import com.dsf.common.persistence.BaseAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "ohdp_device")
public class OhdpDevice extends BaseAuditEntity {

    @Column(name = "device_name", nullable = false, length = 200)
    private String deviceName;

    /** LAB_INSTRUMENT / SAMPLER */
    @Column(name = "device_type", nullable = false, length = 32)
    private String deviceType;

    @Column(name = "device_model", length = 100)
    private String deviceModel;

    @Column(name = "device_no", length = 100)
    private String deviceNo;

    @Column(name = "manufacturer", length = 200)
    private String manufacturer;

    @Column(name = "calibration_date")
    private LocalDate calibrationDate;

    @Column(name = "calibration_expiry")
    private LocalDate calibrationExpiry;

    /** NORMAL / MAINTENANCE / SCRAPPED */
    @Column(name = "status", length = 32)
    private String status = "NORMAL";

    @Column(name = "notes", length = 500)
    private String notes;
}
