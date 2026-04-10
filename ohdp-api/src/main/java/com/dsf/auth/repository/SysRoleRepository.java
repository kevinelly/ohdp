package com.dsf.auth.repository;

import com.dsf.auth.entity.SysRole;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * @author kevin
 * @description 角色仓储
 * @unit Unit 作用说明：
 *       1) unit 表示“最小业务单元”或“计量单位”；
 *       2) 在本模块中用于统一数据口径，避免前后端对同一字段理解不一致；
 *       3) 典型值示例：kg（重量）、box（箱）、item（件）。
 */
public interface SysRoleRepository extends JpaRepository<SysRole, Long> {

    Optional<SysRole> findByIdAndDeletedFalse(Long id);

    Optional<SysRole> findByRoleCodeAndDeletedFalse(String roleCode);

    boolean existsByRoleCodeAndDeletedFalse(String roleCode);

    boolean existsByRoleCodeAndDeletedFalseAndIdNot(String roleCode, Long id);

    Page<SysRole> findByDeletedFalseAndRoleCodeContainingAndRoleNameContaining(String roleCode, String roleName, Pageable pageable);

    List<SysRole> findByIdInAndDeletedFalse(Collection<Long> ids);
}

