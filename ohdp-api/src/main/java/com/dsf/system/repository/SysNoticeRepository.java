package com.dsf.system.repository;

import com.dsf.system.entity.SysNotice;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SysNoticeRepository extends JpaRepository<SysNotice, Long> {

    Page<SysNotice> findByTitleContainingAndDeletedFalse(String title, Pageable pageable);

    Page<SysNotice> findByTitleContainingAndNoticeTypeAndDeletedFalse(String title, String noticeType, Pageable pageable);

    Page<SysNotice> findByDeletedFalse(Pageable pageable);

    Page<SysNotice> findByNoticeTypeAndDeletedFalse(String noticeType, Pageable pageable);

    Optional<SysNotice> findByIdAndDeletedFalse(Long id);
}
