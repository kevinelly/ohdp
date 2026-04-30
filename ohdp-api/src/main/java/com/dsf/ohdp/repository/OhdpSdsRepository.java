package com.dsf.ohdp.repository;

import com.dsf.ohdp.entity.OhdpSds;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OhdpSdsRepository extends JpaRepository<OhdpSds, Long> {

    Page<OhdpSds> findByDeletedFalseAndSubstanceNameContainingAndFactorId(
            String substanceName, Long factorId, Pageable pageable);

    Page<OhdpSds> findByDeletedFalseAndSubstanceNameContaining(
            String substanceName, Pageable pageable);

    Page<OhdpSds> findByDeletedFalseAndFactorId(Long factorId, Pageable pageable);

    Page<OhdpSds> findByDeletedFalse(Pageable pageable);

    Optional<OhdpSds> findByIdAndDeletedFalse(Long id);
}
