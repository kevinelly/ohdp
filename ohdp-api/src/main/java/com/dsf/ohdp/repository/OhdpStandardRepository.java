package com.dsf.ohdp.repository;

import com.dsf.ohdp.entity.OhdpStandard;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OhdpStandardRepository extends JpaRepository<OhdpStandard, Long> {

    Page<OhdpStandard> findByDeletedFalse(Pageable pageable);

    Page<OhdpStandard> findByStandardNameContainingIgnoreCaseAndDeletedFalse(String standardName, Pageable pageable);

    Page<OhdpStandard> findByLevelAndDeletedFalse(String level, Pageable pageable);

    Page<OhdpStandard> findByStandardNameContainingIgnoreCaseAndLevelAndDeletedFalse(String standardName, String level, Pageable pageable);

    Optional<OhdpStandard> findByIdAndDeletedFalse(Long id);
}
