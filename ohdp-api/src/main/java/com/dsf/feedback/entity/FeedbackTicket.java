package com.dsf.feedback.entity;

import com.dsf.common.persistence.BaseAuditEntity;
import com.dsf.feedback.model.FeedbackCategory;
import com.dsf.feedback.model.FeedbackPriority;
import com.dsf.feedback.model.FeedbackStatus;
import com.dsf.feedback.model.FeedbackUrgency;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "feedback_ticket", uniqueConstraints = {
        @UniqueConstraint(name = "uk_feedback_ticket_no", columnNames = "ticket_no")
})
public class FeedbackTicket extends BaseAuditEntity {

    @Column(name = "ticket_no", nullable = false, length = 40)
    private String ticketNo;

    @Column(name = "title", nullable = false, length = 120)
    private String title;

    @Column(name = "description", nullable = false, length = 4000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 32)
    private FeedbackCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "urgency", nullable = false, length = 16)
    private FeedbackUrgency urgency;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 16)
    private FeedbackPriority priority;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private FeedbackStatus status;

    @Column(name = "source_module", length = 100)
    private String sourceModule;

    @Column(name = "source_page", length = 500)
    private String sourcePage;

    @Column(name = "related_type", length = 32)
    private String relatedType;

    @Column(name = "related_code", length = 100)
    private String relatedCode;

    @Column(name = "submitter_id", nullable = false)
    private Long submitterId;

    @Column(name = "submitter_name", nullable = false, length = 64)
    private String submitterName;

    @Column(name = "assignee_id")
    private Long assigneeId;

    @Column(name = "assignee_name", length = 64)
    private String assigneeName;

    @Column(name = "planned_completion_date")
    private LocalDate plannedCompletionDate;

    @Column(name = "planned_version", length = 64)
    private String plannedVersion;

    @Column(name = "resolution_summary", length = 2000)
    private String resolutionSummary;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Version
    @Column(name = "lock_version", nullable = false)
    private Long lockVersion;
}
