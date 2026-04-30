package com.dsf.ohdp.repository;

import com.dsf.ohdp.entity.OhdpDetectionMethod;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OhdpDetectionMethodRepository extends JpaRepository<OhdpDetectionMethod, Long> {

    Optional<OhdpDetectionMethod> findByIdAndDeletedFalse(Long id);

    List<OhdpDetectionMethod> findAllByDeletedFalseOrderByIdAsc();

    List<OhdpDetectionMethod> findAllByDeletedFalseAndEnabledTrueOrderByIdAsc();

    @Query("SELECT m FROM OhdpDetectionMethod m WHERE m.deleted = false" +
            " AND (:enabled IS NULL OR m.enabled = :enabled)" +
            " AND (:keyword IS NULL OR m.methodName LIKE %:keyword% OR m.methodAbbr LIKE %:keyword%)")
    Page<OhdpDetectionMethod> findByPage(@Param("keyword") String keyword,
                                         @Param("enabled") Boolean enabled,
                                         Pageable pageable);
}
