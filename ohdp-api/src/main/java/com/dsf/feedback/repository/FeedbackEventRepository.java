package com.dsf.feedback.repository;

import com.dsf.feedback.entity.FeedbackEvent;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeedbackEventRepository extends JpaRepository<FeedbackEvent, Long> {

    List<FeedbackEvent> findByTicketIdAndDeletedFalseOrderByIdAsc(Long ticketId);

    Optional<FeedbackEvent> findTopByTicketIdAndDeletedFalseOrderByIdDesc(Long ticketId);
}
