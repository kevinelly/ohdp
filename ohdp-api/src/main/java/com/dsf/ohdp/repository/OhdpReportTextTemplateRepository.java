package com.dsf.ohdp.repository;

import com.dsf.ohdp.entity.OhdpReportTextTemplate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OhdpReportTextTemplateRepository extends JpaRepository<OhdpReportTextTemplate, Long> {

    Optional<OhdpReportTextTemplate> findByIdAndDeletedFalse(Long id);

    Page<OhdpReportTextTemplate> findByDeletedFalseAndTemplateTypeAndTemplateNameContaining(
            String templateType,
            String templateName,
            Pageable pageable);

    List<OhdpReportTextTemplate> findByDeletedFalseAndTemplateTypeAndEnabledTrueOrderByIdAsc(String templateType);
}

