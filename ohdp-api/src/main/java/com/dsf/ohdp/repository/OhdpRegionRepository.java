package com.dsf.ohdp.repository;

import com.dsf.ohdp.entity.OhdpRegion;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OhdpRegionRepository extends JpaRepository<OhdpRegion, Long> {

    List<OhdpRegion> findByParentIdAndDeletedFalseOrderBySortNoAsc(Long parentId);

    List<OhdpRegion> findAllByDeletedFalseOrderByLevelAscSortNoAsc();

    Optional<OhdpRegion> findByIdAndDeletedFalse(Long id);
}
