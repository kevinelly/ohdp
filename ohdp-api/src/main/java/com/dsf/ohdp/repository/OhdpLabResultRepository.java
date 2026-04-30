package com.dsf.ohdp.repository;

import com.dsf.ohdp.entity.OhdpLabResult;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OhdpLabResultRepository extends JpaRepository<OhdpLabResult, Long> {

    Page<OhdpLabResult> findByDeletedFalse(Pageable pageable);

    Page<OhdpLabResult> findByTaskIdAndDeletedFalse(Long taskId, Pageable pageable);

    Page<OhdpLabResult> findByFactorNameContainingAndDeletedFalse(String factorName, Pageable pageable);

    Page<OhdpLabResult> findByTaskIdAndFactorNameContainingAndDeletedFalse(Long taskId, String factorName, Pageable pageable);

    Optional<OhdpLabResult> findByIdAndDeletedFalse(Long id);

    List<OhdpLabResult> findByTaskIdAndDeletedFalseOrderByIdAsc(Long taskId);
}
