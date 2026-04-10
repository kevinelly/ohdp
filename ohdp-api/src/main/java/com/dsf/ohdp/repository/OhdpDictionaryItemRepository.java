package com.dsf.ohdp.repository;

import com.dsf.ohdp.entity.OhdpDictionaryItem;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OhdpDictionaryItemRepository extends JpaRepository<OhdpDictionaryItem, Long> {

    Optional<OhdpDictionaryItem> findByIdAndDeletedFalse(Long id);

    List<OhdpDictionaryItem> findByDeletedFalseAndDictTypeOrderBySortNoAscIdAsc(String dictType);
}

