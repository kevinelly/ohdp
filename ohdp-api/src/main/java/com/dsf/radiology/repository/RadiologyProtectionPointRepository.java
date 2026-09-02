package com.dsf.radiology.repository;

import com.dsf.radiology.entity.RadiologyProtectionPoint;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RadiologyProtectionPointRepository extends JpaRepository<RadiologyProtectionPoint, Long> {
    List<RadiologyProtectionPoint> findByCaseIdAndDeletedFalseOrderBySortNoAsc(Long caseId);

    Optional<RadiologyProtectionPoint> findByCaseIdAndPointCodeAndDeletedFalse(Long caseId, String pointCode);
}
