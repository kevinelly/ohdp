package com.dsf.radiology.repository;

import com.dsf.radiology.entity.RadiologyReport;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RadiologyReportRepository extends JpaRepository<RadiologyReport, Long> {
    List<RadiologyReport> findByCaseIdAndDeletedFalseOrderByGeneratedAtDesc(Long caseId);

    Optional<RadiologyReport> findByIdAndDeletedFalse(Long id);
}
