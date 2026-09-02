package com.dsf.ohdp.entity;

import com.dsf.common.persistence.BaseAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "ohdp_reference_document_version",
        indexes = @Index(name = "idx_reference_document_owner", columnList = "reference_type,reference_id"),
        uniqueConstraints = @UniqueConstraint(
                name = "uk_reference_document_version",
                columnNames = {"reference_type", "reference_id", "version_no"}))
public class OhdpReferenceDocumentVersion extends BaseAuditEntity {

    @Column(name = "reference_type", nullable = false, length = 16)
    private String referenceType;

    @Column(name = "reference_id", nullable = false)
    private Long referenceId;

    @Column(name = "version_no", nullable = false)
    private Integer versionNo;

    @Column(name = "version_label", length = 64)
    private String versionLabel;

    @Column(name = "original_name", nullable = false, length = 255)
    private String originalName;

    @Column(name = "stored_name", nullable = false, length = 255)
    private String storedName;

    @Column(name = "storage_path", nullable = false, length = 600)
    private String storagePath;

    @Column(name = "content_type", nullable = false, length = 128)
    private String contentType;

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    @Column(name = "sha256", nullable = false, length = 64)
    private String sha256;

    @Column(name = "source_url", length = 500)
    private String sourceUrl;

    @Column(name = "uploaded_by", length = 64)
    private String uploadedBy;

    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt;
}
