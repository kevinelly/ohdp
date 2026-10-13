package com.dsf.radiology.entity;

import com.dsf.common.persistence.BaseAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "qc_report")
public class RadiologyReport extends BaseAuditEntity {

    @Column(name = "case_id", nullable = false)
    private Long caseId;

    @Column(name = "report_no", nullable = false, length = 100)
    private String reportNo;

    @Column(name = "snapshot_json", nullable = false, columnDefinition = "LONGTEXT")
    private String snapshotJson;

    @Column(name = "file_name", nullable = false, length = 500)
    private String fileName;

    @Column(name = "storage_path", nullable = false, length = 1000)
    private String storagePath;

    @Column(name = "sha256", nullable = false, length = 64)
    private String sha256;

    @Column(name = "generated_by", length = 100)
    private String generatedBy;

    @Column(name="deletion_reason",length=1000)
    private String deletionReason;
    @Column(name="deleted_by",length=100)
    private String deletedBy;
    @Column(name="deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "generated_at", nullable = false)
    private LocalDateTime generatedAt;
}
