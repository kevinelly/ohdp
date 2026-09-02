package com.dsf.ohdp.repository;

import com.dsf.ohdp.entity.OhdpProject;
import com.dsf.ohdp.entity.ProjectStatus;
import java.util.Optional;
import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OhdpProjectRepository extends JpaRepository<OhdpProject, Long> {

    Optional<OhdpProject> findByIdAndDeletedFalse(Long id);

    boolean existsByProjectCodeAndDeletedFalse(String projectCode);

    Page<OhdpProject> findByDeletedFalseAndProjectCodeContainingAndCompanyNameContaining(
            String projectCode, String companyName, Pageable pageable);

    Page<OhdpProject> findByDeletedFalseAndProjectCodeContainingAndCompanyNameContainingAndStatus(
            String projectCode, String companyName, ProjectStatus status, Pageable pageable);

    @Query(value = """
            select p, r from OhdpProject p
            left join OhdpReportFile r on r.projectId = p.id and r.deleted = false
            where p.deleted = false
              and lower(p.projectCode) like lower(concat('%', :projectCode, '%'))
              and lower(p.companyName) like lower(concat('%', :companyName, '%'))
              and (:reportStatus = ''
                   or (:reportStatus = 'GENERATED' and r.id is not null)
                   or (:reportStatus = 'PENDING' and r.id is null))
              and (:startTime is null or r.generatedAt >= :startTime)
              and (:endTime is null or r.generatedAt < :endTime)
            order by p.createdAt desc, p.id desc
            """,
            countQuery = """
            select count(p) from OhdpProject p
            left join OhdpReportFile r on r.projectId = p.id and r.deleted = false
            where p.deleted = false
              and lower(p.projectCode) like lower(concat('%', :projectCode, '%'))
              and lower(p.companyName) like lower(concat('%', :companyName, '%'))
              and (:reportStatus = ''
                   or (:reportStatus = 'GENERATED' and r.id is not null)
                   or (:reportStatus = 'PENDING' and r.id is null))
              and (:startTime is null or r.generatedAt >= :startTime)
              and (:endTime is null or r.generatedAt < :endTime)
            """)
    Page<Object[]> findReportCenterPage(
            @Param("projectCode") String projectCode,
            @Param("companyName") String companyName,
            @Param("reportStatus") String reportStatus,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            Pageable pageable);
}
