package com.dsf.ohdp.repository;

import com.dsf.ohdp.entity.OhdpLabTask;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OhdpLabTaskRepository extends JpaRepository<OhdpLabTask, Long> {

    Optional<OhdpLabTask> findByIdAndDeletedFalse(Long id);

    @Query("SELECT t FROM OhdpLabTask t WHERE t.deleted = false"
            + " AND (:projectId IS NULL OR t.projectId = :projectId)"
            + " AND (:taskStatus IS NULL OR t.taskStatus = :taskStatus)"
            + " AND (:keyword IS NULL OR t.taskCode LIKE %:keyword%)"
            + " ORDER BY t.id DESC")
    Page<OhdpLabTask> findByConditions(
            @Param("projectId") Long projectId,
            @Param("taskStatus") String taskStatus,
            @Param("keyword") String keyword,
            Pageable pageable);
}
