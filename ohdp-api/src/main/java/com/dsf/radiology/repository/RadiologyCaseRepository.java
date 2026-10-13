package com.dsf.radiology.repository;

import com.dsf.radiology.entity.RadiologyCase;
import com.dsf.radiology.model.RadiologyCaseStatus;
import com.dsf.radiology.model.RadiologyModality;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RadiologyCaseRepository extends JpaRepository<RadiologyCase, Long> {
    @org.springframework.data.jpa.repository.Query("""
            select c from RadiologyCase c where c.deleted=false
            and (:modality is null or c.modality=:modality) and (:status is null or c.status=:status)
            and (lower(c.caseCode) like lower(concat('%',:keyword,'%'))
              or lower(c.inspectedUnitName) like lower(concat('%',:keyword,'%'))
              or lower(coalesce(c.equipmentNo,'')) like lower(concat('%',:keyword,'%'))
              or lower(coalesce(c.sampleAcceptanceNo,'')) like lower(concat('%',:keyword,'%'))
              or lower(coalesce(c.region,'')) like lower(concat('%',:keyword,'%')))
            """)
    Page<RadiologyCase> search(RadiologyModality modality,RadiologyCaseStatus status,String keyword,Pageable pageable);

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
