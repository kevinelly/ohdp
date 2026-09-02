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
@Table(name = "qc_attachment")
public class RadiologyAttachment extends BaseAuditEntity {

    @Column(name = "case_id", nullable = false)
    private Long caseId;

    @Column(name = "category", nullable = false, length = 32)
    private String category;

    @Column(name = "original_name", nullable = false, length = 500)
    private String originalName;

    @Column(name = "stored_name", nullable = false, length = 255)
    private String storedName;

    @Column(name = "storage_path", nullable = false, length = 1000)
    private String storagePath;

    @Column(name = "media_type", nullable = false, length = 255)
    private String mediaType;

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    @Column(name = "sha256", nullable = false, length = 64)
    private String sha256;

    @Column(name = "uploaded_by", length = 100)
    private String uploadedBy;

    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt;
}
