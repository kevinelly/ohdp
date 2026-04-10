package com.dsf.auth.repository;

import com.dsf.auth.entity.PermissionType;
import com.dsf.auth.entity.SysPermission;
import com.dsf.common.model.RecordStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * @author kevin
 * @description 权限仓储
 * @unit Unit 作用说明：
 *       1) unit 表示“最小业务单元”或“计量单位”；
 *       2) 在本模块中用于统一数据口径，避免前后端对同一字段理解不一致；
 *       3) 典型值示例：kg（重量）、box（箱）、item（件）。
 */
public interface SysPermissionRepository extends JpaRepository<SysPermission, Long> {

    Optional<SysPermission> findByIdAndDeletedFalse(Long id);

    boolean existsByPermCodeAndDeletedFalse(String permCode);

    boolean existsByPermCodeAndDeletedFalseAndIdNot(String permCode, Long id);

    Page<SysPermission> findByDeletedFalseAndPermCodeContainingAndPermNameContaining(
            String permCode, String permName, Pageable pageable);

    List<SysPermission> findByDeletedFalseAndStatusAndPermType(RecordStatus status, PermissionType permType);

    List<SysPermission> findByDeletedFalseAndStatusAndPermTypeInOrderBySortNoAscIdAsc(
            RecordStatus status, Collection<PermissionType> permTypes);

    List<SysPermission> findByDeletedFalseAndStatusOrderBySortNoAscIdAsc(RecordStatus status);

    List<SysPermission> findByIdInAndDeletedFalse(Collection<Long> ids);

    List<SysPermission> findByIdInAndDeletedFalseOrderBySortNoAscIdAsc(Collection<Long> ids);
}
