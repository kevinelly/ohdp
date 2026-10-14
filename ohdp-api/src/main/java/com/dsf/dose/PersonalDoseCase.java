package com.dsf.dose;

import com.dsf.common.persistence.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity @Table(name="personal_dose_case") @Getter @Setter
public class PersonalDoseCase extends BaseAuditEntity {
    @Column(nullable=false,length=100) private String receipt;
    @Column(nullable=false) private String unitName;
    @Column(nullable=false,length=32) private String status="DRAFT";
    @Column(columnDefinition="longtext",nullable=false) private String payload;
    @Column(columnDefinition="longtext") private String history="[]";
    @Column(columnDefinition="longtext") private String approvedSnapshot;
    @Lob @Column(columnDefinition="longblob") private byte[] report;
    private String reportSha256;
    private Long sourceCaseId;
    @Version private Long version;
}
