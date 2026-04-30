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
@Table(name = "ohdp_standard")
public class OhdpStandard extends BaseAuditEntity {

    @Column(name = "standard_name", nullable = false, length = 300)
    private String standardName;

    @Column(name = "standard_code", unique = true, length = 128)
    private String standardCode;

    /** NATIONAL / INDUSTRY */
    @Column(name = "level", length = 32)
    private String level;

    @Column(name = "use_scope", length = 100)
    private String useScope;

    @Column(name = "publish_date")
    private LocalDate publishDate;

    @Column(name = "is_valid")
    private Boolean isValid = true;

    @Column(name = "is_open")
    private Boolean isOpen = true;

    @Column(name = "file_id")
    private Long fileId;

    @Column(name = "notes", length = 500)
    private String notes;
}
