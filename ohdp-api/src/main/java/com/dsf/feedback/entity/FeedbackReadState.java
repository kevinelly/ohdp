package com.dsf.feedback.entity;

import com.dsf.common.persistence.BaseAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "feedback_read_state", uniqueConstraints = {
        @UniqueConstraint(name = "uk_feedback_read_ticket_user", columnNames = {"ticket_id", "user_id"})
})
public class FeedbackReadState extends BaseAuditEntity {

    @Column(name = "ticket_id", nullable = false)
    private Long ticketId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "last_read_event_id")
    private Long lastReadEventId;

    @Column(name = "read_at", nullable = false)
    private LocalDateTime readAt;
}
