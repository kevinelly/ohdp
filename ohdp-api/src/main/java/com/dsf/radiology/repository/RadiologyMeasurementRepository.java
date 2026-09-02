package com.dsf.radiology.repository;

import com.dsf.radiology.entity.RadiologyMeasurement;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RadiologyMeasurementRepository extends JpaRepository<RadiologyMeasurement, Long> {
    List<RadiologyMeasurement> findByCaseIdAndDeletedFalseOrderByItemCodeAscSortNoAsc(Long caseId);

    List<RadiologyMeasurement> findByCaseIdAndItemCodeAndDeletedFalseOrderBySortNoAsc(Long caseId, String itemCode);

    Optional<RadiologyMeasurement> findByCaseIdAndItemCodeAndFieldCodeAndDeletedFalse(
            Long caseId, String itemCode, String fieldCode);

    Optional<RadiologyMeasurement> findByCaseIdAndItemCodeAndFieldCode(Long caseId, String itemCode, String fieldCode);
}
