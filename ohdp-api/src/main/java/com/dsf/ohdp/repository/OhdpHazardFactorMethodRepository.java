package com.dsf.ohdp.repository;

import com.dsf.ohdp.entity.OhdpHazardFactorMethod;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OhdpHazardFactorMethodRepository extends JpaRepository<OhdpHazardFactorMethod, Long> {

    List<OhdpHazardFactorMethod> findByFactorIdAndDeletedFalse(Long factorId);

    Optional<OhdpHazardFactorMethod> findByFactorIdAndMethodIdAndDeletedFalse(Long factorId, Long methodId);

    Optional<OhdpHazardFactorMethod> findByIdAndDeletedFalse(Long id);
}
