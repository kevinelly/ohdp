package com.dsf.radiology.repository;

import com.dsf.radiology.entity.RadiologyCase;
import com.dsf.radiology.model.RadiologyCaseStatus;
import com.dsf.radiology.model.RadiologyModality;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RadiologyCaseRepository extends JpaRepository<RadiologyCase, Long> {
    Optional<RadiologyCase> findByIdAndDeletedFalse(Long id);

    boolean existsByCaseCodeAndDeletedFalse(String caseCode);

    Page<RadiologyCase> findByDeletedFalseAndModalityAndStatusAndCaseCodeContainingIgnoreCase(
            RadiologyModality modality, RadiologyCaseStatus status, String keyword, Pageable pageable);

    Page<RadiologyCase> findByDeletedFalseAndModalityAndCaseCodeContainingIgnoreCase(
            RadiologyModality modality, String keyword, Pageable pageable);

    Page<RadiologyCase> findByDeletedFalseAndStatusAndCaseCodeContainingIgnoreCase(
            RadiologyCaseStatus status, String keyword, Pageable pageable);

    Page<RadiologyCase> findByDeletedFalseAndCaseCodeContainingIgnoreCase(String keyword, Pageable pageable);
}
