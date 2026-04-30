package com.dsf.ohdp.entity;

import com.dsf.common.persistence.BaseAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/**
 * @author kevin
 * @description 实验室任务单样品明细
 */
@Getter
@Setter
@Entity
@Table(name = "ohdp_lab_task_sample")
public class OhdpLabTaskSample extends BaseAuditEntity {

    @Column(name = "task_id", nullable = false)
    private Long taskId;

    @Column(name = "sample_code", nullable = false, length = 64)
    private String sampleCode;

    @Column(name = "factor_name", nullable = false, length = 200)
    private String factorName;

    @Column(name = "factor_id")
    private Long factorId;

    @Column(name = "sample_count")
    private Integer sampleCount;

    @Column(name = "sample_date")
    private LocalDate sampleDate;

    @Column(name = "sample_type", length = 32)
    private String sampleType;

    @Column(name = "notes", length = 500)
    private String notes;
}
