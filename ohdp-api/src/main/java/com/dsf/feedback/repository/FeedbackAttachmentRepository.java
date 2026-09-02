package com.dsf.feedback.repository;

import com.dsf.feedback.entity.FeedbackAttachment;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeedbackAttachmentRepository extends JpaRepository<FeedbackAttachment, Long> {

    List<FeedbackAttachment> findByTicketIdAndDeletedFalseOrderByUploadedAtDesc(Long ticketId);

    Optional<FeedbackAttachment> findByIdAndDeletedFalse(Long id);
}
