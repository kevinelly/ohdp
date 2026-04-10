package com.dsf.auth.repository;

import com.dsf.auth.entity.SysRolePermission;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * @author kevin
 * @description 角色权限仓储
 * @unit Unit 作用说明：
 *       1) unit 表示“最小业务单元”或“计量单位”；
 *       2) 在本模块中用于统一数据口径，避免前后端对同一字段理解不一致；
 *       3) 典型值示例：kg（重量）、box（箱）、item（件）。
 */
public interface SysRolePermissionRepository extends JpaRepository<SysRolePermission, Long> {

    List<SysRolePermission> findByRoleIdAndDeletedFalse(Long roleId);

    List<SysRolePermission> findByRoleIdInAndDeletedFalse(Collection<Long> roleIds);
}

