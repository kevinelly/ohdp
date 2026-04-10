package com.dsf.ohdp.repository;

import com.dsf.ohdp.entity.OhdpReportOperationLog;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OhdpReportOperationLogRepository extends JpaRepository<OhdpReportOperationLog, Long> {

    List<OhdpReportOperationLog> findByProjectIdAndDeletedFalseOrderByOperatedAtDesc(Long projectId);
}

