package com.dsf.ohdp.repository;

import com.dsf.ohdp.entity.OhdpStaffProfile;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OhdpStaffProfileRepository extends JpaRepository<OhdpStaffProfile, Long> {

    Optional<OhdpStaffProfile> findByIdAndDeletedFalse(Long id);

    Page<OhdpStaffProfile> findByDeletedFalseAndStaffNameContaining(String staffName, Pageable pageable);
}

