package com.dsf.ohdp.repository;

import com.dsf.ohdp.entity.OhdpReferenceDocumentVersion;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OhdpReferenceDocumentVersionRepository extends JpaRepository<OhdpReferenceDocumentVersion, Long> {

    List<OhdpReferenceDocumentVersion> findByReferenceTypeAndReferenceIdAndDeletedFalseOrderByVersionNoDesc(
            String referenceType, Long referenceId);

    Optional<OhdpReferenceDocumentVersion> findFirstByReferenceTypeAndReferenceIdAndDeletedFalseOrderByVersionNoDesc(
            String referenceType, Long referenceId);

    Optional<OhdpReferenceDocumentVersion> findByIdAndDeletedFalse(Long id);
}
