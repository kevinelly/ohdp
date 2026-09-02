package com.dsf.feedback.repository;

import com.dsf.feedback.entity.FeedbackTicket;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface FeedbackTicketRepository extends JpaRepository<FeedbackTicket, Long>, JpaSpecificationExecutor<FeedbackTicket> {

    Optional<FeedbackTicket> findByIdAndDeletedFalse(Long id);
}
