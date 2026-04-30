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
@Table(name = "ohdp_law")
public class OhdpLaw extends BaseAuditEntity {

    @Column(name = "title", length = 300)
    private String title;

    @Column(name = "doc_no", length = 128)
    private String docNo;

    /** NATIONAL / PROVINCIAL / LOCAL */
    @Column(name = "level", length = 32)
    private String level;

    @Column(name = "publish_date")
    private LocalDate publishDate;

    @Column(name = "effective_date")
    private LocalDate effectiveDate;

    @Column(name = "publisher", length = 100)
    private String publisher;

    @Column(name = "is_valid")
    private Boolean isValid = true;

    @Column(name = "is_open")
    private Boolean isOpen = true;

    @Column(name = "file_id")
    private Long fileId;

    @Column(name = "notes", length = 500)
    private String notes;
}
