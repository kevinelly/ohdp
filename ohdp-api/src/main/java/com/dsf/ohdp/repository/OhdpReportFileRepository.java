package com.dsf.ohdp.repository;

import com.dsf.ohdp.entity.OhdpReportFile;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OhdpReportFileRepository extends JpaRepository<OhdpReportFile, Long> {

    Optional<OhdpReportFile> findByIdAndDeletedFalse(Long id);

    Optional<OhdpReportFile> findByProjectIdAndDeletedFalse(Long projectId);

    List<OhdpReportFile> findByProjectIdAndDeletedFalseOrderByGeneratedAtDesc(Long projectId);
}
