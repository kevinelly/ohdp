package com.dsf.feedback.entity;

import com.dsf.common.persistence.BaseAuditEntity;
import com.dsf.feedback.model.FeedbackEventType;
import com.dsf.feedback.model.FeedbackStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "feedback_event")
public class FeedbackEvent extends BaseAuditEntity {

    @Column(name = "ticket_id", nullable = false)
    private Long ticketId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 32)
    private FeedbackEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 32)
    private FeedbackStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", length = 32)
    private FeedbackStatus toStatus;

    @Column(name = "content", length = 4000)
    private String content;

    @Column(name = "operator_id", nullable = false)
    private Long operatorId;

    @Column(name = "operator_name", nullable = false, length = 64)
    private String operatorName;

    @Column(name = "internal_only", nullable = false)
    private Boolean internalOnly;
}
