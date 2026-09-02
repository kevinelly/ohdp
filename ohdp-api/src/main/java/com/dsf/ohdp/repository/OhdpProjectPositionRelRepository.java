package com.dsf.ohdp.repository;

import com.dsf.ohdp.entity.OhdpProjectPositionRel;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OhdpProjectPositionRelRepository extends JpaRepository<OhdpProjectPositionRel, Long> {

    List<OhdpProjectPositionRel> findByProjectIdAndDeletedFalseOrderByIdAsc(Long projectId);

    List<OhdpProjectPositionRel> findByPositionIdAndDeletedFalseOrderByIdAsc(Long positionId);

    Optional<OhdpProjectPositionRel> findByProjectIdAndPositionIdAndDeletedFalse(Long projectId, Long positionId);
}

