package com.dsf.ohdp.repository;

import com.dsf.ohdp.entity.OhdpResultItem;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OhdpResultItemRepository extends JpaRepository<OhdpResultItem, Long> {

    List<OhdpResultItem> findByProjectIdAndDeletedFalseOrderByIdAsc(Long projectId);

    Optional<OhdpResultItem> findByIdAndDeletedFalse(Long id);
}
