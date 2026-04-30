package com.dsf.ohdp.repository;

import com.dsf.ohdp.entity.OhdpHazardFactorLimit;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OhdpHazardFactorLimitRepository extends JpaRepository<OhdpHazardFactorLimit, Long> {

    List<OhdpHazardFactorLimit> findByFactorIdAndDeletedFalseOrderByLimitTypeAsc(Long factorId);

    Optional<OhdpHazardFactorLimit> findByIdAndDeletedFalse(Long id);
}
