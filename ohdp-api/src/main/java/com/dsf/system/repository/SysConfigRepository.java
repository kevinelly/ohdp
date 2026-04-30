package com.dsf.system.repository;

import com.dsf.system.entity.SysConfig;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SysConfigRepository extends JpaRepository<SysConfig, Long> {

    Optional<SysConfig> findByConfigKeyAndDeletedFalse(String configKey);

    Optional<SysConfig> findByIdAndDeletedFalse(Long id);

    @Query("SELECT c FROM SysConfig c WHERE c.deleted = false AND " +
            "(:keyword IS NULL OR c.configKey LIKE %:keyword% OR c.configName LIKE %:keyword%)")
    Page<SysConfig> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);
}
