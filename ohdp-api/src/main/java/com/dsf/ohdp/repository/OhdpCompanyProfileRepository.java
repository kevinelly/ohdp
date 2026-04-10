package com.dsf.ohdp.repository;

import com.dsf.ohdp.entity.OhdpCompanyProfile;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OhdpCompanyProfileRepository extends JpaRepository<OhdpCompanyProfile, Long> {

    Optional<OhdpCompanyProfile> findByIdAndDeletedFalse(Long id);

    boolean existsByCompanyNameAndDeletedFalse(String companyName);

    boolean existsByCompanyNameAndDeletedFalseAndIdNot(String companyName, Long id);

    Page<OhdpCompanyProfile> findByDeletedFalseAndCompanyNameContaining(String companyName, Pageable pageable);
}

