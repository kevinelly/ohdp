package com.dsf.ohdp.repository;

import com.dsf.ohdp.entity.OhdpOrgConfig;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OhdpOrgConfigRepository extends JpaRepository<OhdpOrgConfig, Long> {

    Optional<OhdpOrgConfig> findFirstByDeletedFalse();
}
