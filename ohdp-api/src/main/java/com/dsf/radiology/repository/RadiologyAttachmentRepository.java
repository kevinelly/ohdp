package com.dsf.radiology.repository;

import com.dsf.radiology.entity.RadiologyAttachment;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RadiologyAttachmentRepository extends JpaRepository<RadiologyAttachment, Long> {
    List<RadiologyAttachment> findByCaseIdAndDeletedFalseOrderByUploadedAtDesc(Long caseId);

    Optional<RadiologyAttachment> findByIdAndDeletedFalse(Long id);
}
