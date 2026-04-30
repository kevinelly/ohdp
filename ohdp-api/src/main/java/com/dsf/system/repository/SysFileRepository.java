package com.dsf.system.repository;

import com.dsf.system.entity.SysFile;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SysFileRepository extends JpaRepository<SysFile, Long> {

    Page<SysFile> findByDeletedFalse(Pageable pageable);

    Page<SysFile> findByBizTypeAndDeletedFalse(String bizType, Pageable pageable);

    Page<SysFile> findByBizIdAndDeletedFalse(Long bizId, Pageable pageable);

    Page<SysFile> findByBizTypeAndBizIdAndDeletedFalse(String bizType, Long bizId, Pageable pageable);

    Optional<SysFile> findByIdAndDeletedFalse(Long id);
}
