package com.dsf.ohdp.entity;

import com.dsf.common.persistence.BaseAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * @author kevin
 * @description 实验室内部检测任务单
 */
@Getter
@Setter
@Entity
@Table(name = "ohdp_lab_task", uniqueConstraints = {
        @UniqueConstraint(name = "uk_ohdp_lab_task_code", columnNames = "task_code")
})
public class OhdpLabTask extends BaseAuditEntity {

    @Column(name = "task_code", nullable = false, length = 64)
    private String taskCode;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "task_status", nullable = false, length = 32)
    private String taskStatus;

    @Column(name = "issuer_name", length = 64)
    private String issuerName;

    @Column(name = "issued_date")
    private LocalDate issuedDate;

    @Column(name = "receiver_name", length = 64)
    private String receiverName;

    @Column(name = "start_time")
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    @Column(name = "reviewer_name", length = 64)
    private String reviewerName;

    @Column(name = "review_note", length = 500)
    private String reviewNote;

    @Column(name = "notes", length = 1000)
    private String notes;
}
