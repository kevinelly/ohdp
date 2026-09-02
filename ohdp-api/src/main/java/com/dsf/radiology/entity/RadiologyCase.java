package com.dsf.radiology.entity;

import com.dsf.common.persistence.BaseAuditEntity;
import com.dsf.radiology.model.RadiologyCaseStatus;
import com.dsf.radiology.model.RadiologyModality;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "qc_inspection_case", uniqueConstraints = @UniqueConstraint(name = "uk_qc_case_code", columnNames = "case_code"))
public class RadiologyCase extends BaseAuditEntity {

    @Column(name = "case_code", nullable = false, length = 64)
    private String caseCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "modality", nullable = false, length = 8)
    private RadiologyModality modality;

    @Column(name = "revision_no", nullable = false)
    private Integer revisionNo = 1;

    @Column(name = "source_case_id")
    private Long sourceCaseId;

    @Column(name = "sample_acceptance_no", length = 100)
    private String sampleAcceptanceNo;

    @Column(name = "inspected_unit_name", nullable = false, length = 255)
    private String inspectedUnitName;

    @Column(name = "inspected_unit_address", length = 500)
    private String inspectedUnitAddress;

    @Column(name = "contact_name", length = 100)
    private String contactName;

    @Column(name = "contact_phone", length = 100)
    private String contactPhone;

    @Column(name = "equipment_name", nullable = false, length = 255)
    private String equipmentName;

    @Column(name = "equipment_model", length = 255)
    private String equipmentModel;

    @Column(name = "equipment_no", length = 255)
    private String equipmentNo;

    @Column(name = "manufacturer", length = 255)
    private String manufacturer;

    @Column(name = "phantom_name", length = 255)
    private String phantomName;

    @Column(name = "phantom_manufacturer", length = 255)
    private String phantomManufacturer;

    @Column(name = "phantom_model", length = 255)
    private String phantomModel;

    @Column(name = "phantom_serial_no", length = 255)
    private String phantomSerialNo;

    @Column(name = "phantom_manual_reference", length = 1000)
    private String phantomManualReference;

    @Column(name = "detection_category", length = 255)
    private String detectionCategory;

    @Column(name = "detection_location", length = 500)
    private String detectionLocation;

    @Column(name = "inspection_date")
    private LocalDate inspectionDate;

    @Column(name = "report_date")
    private LocalDate reportDate;

    @Column(name = "standard_basis", length = 1000)
    private String standardBasis;

    @Column(name = "instrument_description", length = 2000)
    private String instrumentDescription;

    @Column(name = "writer_name", length = 100)
    private String writerName;

    @Column(name = "reviewer_name", length = 100)
    private String reviewerName;

    @Column(name = "approver_name", length = 100)
    private String approverName;

    @Column(name = "review_comment", length = 1000)
    private String reviewComment;

    @Column(name = "approval_comment", length = 1000)
    private String approvalComment;

    @Column(name = "notes", length = 2000)
    private String notes;

    @Column(name = "calculation_version", nullable = false, length = 50)
    private String calculationVersion = "2026.1";

    @Enumerated(EnumType.STRING)
    @Column(name = "case_status", nullable = false, length = 32)
    private RadiologyCaseStatus status = RadiologyCaseStatus.DRAFT;
}
