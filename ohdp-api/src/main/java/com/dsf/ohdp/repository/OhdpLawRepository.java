package com.dsf.ohdp.repository;

import com.dsf.ohdp.entity.OhdpLaw;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OhdpLawRepository extends JpaRepository<OhdpLaw, Long> {

    Page<OhdpLaw> findByDeletedFalse(Pageable pageable);

    Page<OhdpLaw> findByTitleContainingIgnoreCaseAndDeletedFalse(String title, Pageable pageable);

    Page<OhdpLaw> findByLevelAndDeletedFalse(String level, Pageable pageable);

    Page<OhdpLaw> findByTitleContainingIgnoreCaseAndLevelAndDeletedFalse(String title, String level, Pageable pageable);

    Optional<OhdpLaw> findByIdAndDeletedFalse(Long id);
}
