package com.dsf.ohdp.repository;

import com.dsf.ohdp.entity.OhdpProject;
import com.dsf.ohdp.entity.ProjectStatus;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OhdpProjectRepository extends JpaRepository<OhdpProject, Long> {

    Optional<OhdpProject> findByIdAndDeletedFalse(Long id);

    boolean existsByProjectCodeAndDeletedFalse(String projectCode);

    Page<OhdpProject> findByDeletedFalseAndProjectCodeContainingAndCompanyNameContaining(
            String projectCode, String companyName, Pageable pageable);

    Page<OhdpProject> findByDeletedFalseAndProjectCodeContainingAndCompanyNameContainingAndStatus(
            String projectCode, String companyName, ProjectStatus status, Pageable pageable);
}
