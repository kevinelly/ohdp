package com.dsf.ohdp.repository;

import com.dsf.ohdp.entity.OhdpProjectPosition;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OhdpProjectPositionRepository extends JpaRepository<OhdpProjectPosition, Long> {

    List<OhdpProjectPosition> findByProjectIdAndDeletedFalseOrderByIdAsc(Long projectId);

    Optional<OhdpProjectPosition> findByIdAndDeletedFalse(Long id);
}
