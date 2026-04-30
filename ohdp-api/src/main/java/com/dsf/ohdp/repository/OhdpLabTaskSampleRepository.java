package com.dsf.ohdp.repository;

import com.dsf.ohdp.entity.OhdpLabTaskSample;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OhdpLabTaskSampleRepository extends JpaRepository<OhdpLabTaskSample, Long> {

    List<OhdpLabTaskSample> findByTaskIdAndDeletedFalseOrderByIdAsc(Long taskId);

    long countByTaskIdAndDeletedFalse(Long taskId);

    Optional<OhdpLabTaskSample> findByIdAndDeletedFalse(Long id);
}
