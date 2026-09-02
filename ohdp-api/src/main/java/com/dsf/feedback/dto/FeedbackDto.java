package com.dsf.feedback.dto;

import com.dsf.feedback.model.FeedbackCategory;
import com.dsf.feedback.model.FeedbackEventType;
import com.dsf.feedback.model.FeedbackPriority;
import com.dsf.feedback.model.FeedbackStatus;
import com.dsf.feedback.model.FeedbackUrgency;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class FeedbackDto {

    private FeedbackDto() {
    }

    public record CreateRequest(
            @NotBlank @Size(min = 5, max = 120) String title,
            @NotBlank @Size(min = 10, max = 4000) String description,
            @NotNull FeedbackCategory category,
            FeedbackUrgency urgency,
            @Size(max = 100) String sourceModule,
            @Size(max = 500) String sourcePage,
            @Size(max = 32) String relatedType,
            @Size(max = 100) String relatedCode) {
    }

    public record HandlingRequest(
            Long assigneeId,
            Boolean assignToMe,
            FeedbackPriority priority,
            LocalDate plannedCompletionDate,
            @Size(max = 64) String plannedVersion) {
    }

    public record CommentRequest(
            @NotBlank @Size(max = 4000) String content,
            Boolean internalOnly) {
    }

    public record TransitionRequest(
            @NotNull FeedbackStatus targetStatus,
            @Size(max = 4000) String comment,
            @Size(max = 2000) String resolutionSummary) {
    }

    public record TicketSummary(
            Long id,
            String ticketNo,
            String title,
            FeedbackCategory category,
            String categoryLabel,
            FeedbackUrgency urgency,
            String urgencyLabel,
            FeedbackPriority priority,
            String priorityLabel,
            FeedbackStatus status,
            String statusLabel,
            String sourceModule,
            String relatedCode,
            Long submitterId,
            String submitterName,
            Long assigneeId,
            String assigneeName,
            LocalDate plannedCompletionDate,
            String plannedVersion,
            boolean unread,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
    }

    public record TicketDetail(
            TicketSummary ticket,
            String description,
            String sourcePage,
            String relatedType,
            String resolutionSummary,
            LocalDateTime resolvedAt,
            LocalDateTime closedAt,
            List<EventResponse> events,
            List<AttachmentResponse> attachments,
            List<FeedbackStatus> allowedTransitions,
            boolean canHandle,
            boolean canClose,
            boolean owner) {
    }

    public record EventResponse(
            Long id,
            FeedbackEventType eventType,
            String eventLabel,
            FeedbackStatus fromStatus,
            FeedbackStatus toStatus,
            String content,
            String operatorName,
            boolean internalOnly,
            LocalDateTime createdAt) {
    }

    public record AttachmentResponse(
            Long id,
            String originalName,
            String mediaType,
            Long fileSize,
            String sha256,
            String uploaderName,
            LocalDateTime uploadedAt,
            boolean canDelete) {
    }

    public record SummaryResponse(long total, long unread, long accepted, long inProgress, long needInfo,
                                  long pendingConfirmation, long closed) {
    }
}
