package com.dsf.ohdp.repository;

import com.dsf.ohdp.entity.OhdpFactorTemplate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OhdpFactorTemplateRepository extends JpaRepository<OhdpFactorTemplate, Long> {

    Optional<OhdpFactorTemplate> findByIdAndDeletedFalse(Long id);

    Page<OhdpFactorTemplate> findByDeletedFalseAndFactorNameContaining(String factorName, Pageable pageable);

    List<OhdpFactorTemplate> findByDeletedFalseAndFactorName(String factorName);
}

