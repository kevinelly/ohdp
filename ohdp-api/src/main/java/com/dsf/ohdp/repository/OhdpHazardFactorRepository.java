package com.dsf.ohdp.repository;

import com.dsf.ohdp.entity.OhdpHazardFactor;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OhdpHazardFactorRepository extends JpaRepository<OhdpHazardFactor, Long> {

    Page<OhdpHazardFactor> findByDeletedFalseAndFactorNameContainingAndFactorCategory(
            String factorName, String factorCategory, Pageable pageable);

    Page<OhdpHazardFactor> findByDeletedFalseAndFactorNameContaining(
            String factorName, Pageable pageable);

    Page<OhdpHazardFactor> findByDeletedFalseAndFactorCategory(
            String factorCategory, Pageable pageable);

    Page<OhdpHazardFactor> findByDeletedFalse(Pageable pageable);

    Optional<OhdpHazardFactor> findByIdAndDeletedFalse(Long id);

    List<OhdpHazardFactor> findByDeletedFalseOrderByFactorCategoryAscSortNoAsc();
}
