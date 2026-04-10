package com.dsf.ohdp.repository;

import com.dsf.ohdp.entity.OhdpPositionTemplate;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OhdpPositionTemplateRepository extends JpaRepository<OhdpPositionTemplate, Long> {

    Optional<OhdpPositionTemplate> findByIdAndDeletedFalse(Long id);

    Page<OhdpPositionTemplate> findByDeletedFalseAndJobNameContaining(String jobName, Pageable pageable);
}

