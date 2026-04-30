package com.dsf.ohdp.repository;

import com.dsf.ohdp.entity.OhdpDetectType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OhdpDetectTypeRepository extends JpaRepository<OhdpDetectType, Long> {

    Optional<OhdpDetectType> findByIdAndDeletedFalse(Long id);

    List<OhdpDetectType> findAllByDeletedFalseOrderBySortNoAsc();

    List<OhdpDetectType> findAllByDeletedFalseAndEnabledTrueOrderBySortNoAsc();
}
