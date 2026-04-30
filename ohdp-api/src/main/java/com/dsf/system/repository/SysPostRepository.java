package com.dsf.system.repository;

import com.dsf.system.entity.SysPost;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SysPostRepository extends JpaRepository<SysPost, Long> {

    Page<SysPost> findByPostNameContainingAndDeletedFalse(String postName, Pageable pageable);

    Page<SysPost> findByPostNameContainingAndEnabledAndDeletedFalse(String postName, Boolean enabled, Pageable pageable);

    Page<SysPost> findByDeletedFalse(Pageable pageable);

    Page<SysPost> findByEnabledAndDeletedFalse(Boolean enabled, Pageable pageable);

    List<SysPost> findAllByDeletedFalseOrderBySortNoAsc();

    List<SysPost> findAllByEnabledAndDeletedFalseOrderBySortNoAsc(Boolean enabled);

    Optional<SysPost> findByIdAndDeletedFalse(Long id);
}
