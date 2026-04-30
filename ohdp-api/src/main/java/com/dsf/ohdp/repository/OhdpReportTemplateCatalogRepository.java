package com.dsf.ohdp.repository;

import com.dsf.ohdp.entity.OhdpReportTemplateCatalog;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OhdpReportTemplateCatalogRepository extends JpaRepository<OhdpReportTemplateCatalog, Long> {

    Optional<OhdpReportTemplateCatalog> findByIdAndDeletedFalse(Long id);

    List<OhdpReportTemplateCatalog> findAllByDeletedFalseOrderBySortNoAsc();

    List<OhdpReportTemplateCatalog> findByDetectTypeIdAndDeletedFalseOrderBySortNoAsc(Long detectTypeId);

    List<OhdpReportTemplateCatalog> findByParentIdAndDeletedFalse(Long parentId);
}
