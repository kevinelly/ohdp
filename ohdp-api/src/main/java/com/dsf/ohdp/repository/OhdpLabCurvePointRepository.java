package com.dsf.ohdp.repository;

import com.dsf.ohdp.entity.OhdpLabCurvePoint;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OhdpLabCurvePointRepository extends JpaRepository<OhdpLabCurvePoint, Long> {

    List<OhdpLabCurvePoint> findByLabResultIdAndDeletedFalseOrderByPointIndexAsc(Long labResultId);

    List<OhdpLabCurvePoint> findByLabResultIdAndIsExcludedFalseAndDeletedFalseOrderByPointIndexAsc(Long labResultId);

    List<OhdpLabCurvePoint> findByLabResultIdAndDeletedFalse(Long labResultId);

    Optional<OhdpLabCurvePoint> findByIdAndDeletedFalse(Long id);
}
