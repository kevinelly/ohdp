package com.dsf.feedback.repository;

import com.dsf.feedback.entity.FeedbackReadState;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeedbackReadStateRepository extends JpaRepository<FeedbackReadState, Long> {

    Optional<FeedbackReadState> findByTicketIdAndUserIdAndDeletedFalse(Long ticketId, Long userId);
}
