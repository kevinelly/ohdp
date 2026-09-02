package com.dsf.auth.service.rbac;

import com.dsf.auth.dto.rbac.AssignIdsRequest;
import com.dsf.auth.entity.SysPermission;
import com.dsf.auth.entity.SysRole;
import com.dsf.auth.entity.SysRolePermission;
import com.dsf.auth.repository.SysPermissionRepository;
import com.dsf.auth.repository.SysRolePermissionRepository;
import com.dsf.auth.repository.SysRoleRepository;
import com.dsf.common.exception.BusinessException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author kevin
 * @description 角色权限绑定服务
 * @unit Unit 作用说明：
 *       1) unit 表示“最小业务单元”或“计量单位”；
 *       2) 在本模块中用于统一数据口径，避免前后端对同一字段理解不一致；
 *       3) 典型值示例：kg（重量）、box（箱）、item（件）。
 */
@Service
@RequiredArgsConstructor
public class RolePermissionService {

    private final SysRoleRepository sysRoleRepository;
    private final SysPermissionRepository sysPermissionRepository;
    private final SysRolePermissionRepository sysRolePermissionRepository;

    @Transactional
    public void assignRolePermissions(Long roleId, AssignIdsRequest request) {
        SysRole role = sysRoleRepository.findByIdAndDeletedFalse(roleId)
                .orElseThrow(() -> new BusinessException("role not found"));
        List<Long> ids = request.getIds() == null ? List.of() : request.getIds();
        List<SysPermission> permissions = ids.isEmpty() ? List.of() : sysPermissionRepository.findByIdInAndDeletedFalse(ids);
        if (permissions.size() != new HashSet<>(ids).size()) {
            throw new BusinessException("permission contains invalid id");
        }

        Set<Long> dedupIds = new HashSet<>(ids);
        List<SysRolePermission> activeRelations = sysRolePermissionRepository.findByRoleIdAndDeletedFalse(role.getId());
        for (SysRolePermission item : activeRelations) {
            if (!dedupIds.contains(item.getPermissionId())) {
                item.setDeleted(true);
            }
        }
        sysRolePermissionRepository.saveAll(activeRelations);

        for (Long permissionId : dedupIds) {
            SysRolePermission relation = sysRolePermissionRepository
                    .findByRoleIdAndPermissionId(role.getId(), permissionId)
                    .orElseGet(() -> {
                        SysRolePermission created = new SysRolePermission();
                        created.setRoleId(role.getId());
                        created.setPermissionId(permissionId);
                        return created;
                    });
            relation.setDeleted(false);
            sysRolePermissionRepository.save(relation);
        }
    }

    @Transactional(readOnly = true)
    public Set<Long> getPermissionIdsByRoleId(Long roleId) {
        sysRoleRepository.findByIdAndDeletedFalse(roleId)
                .orElseThrow(() -> new BusinessException("role not found"));
        return sysRolePermissionRepository.findByRoleIdAndDeletedFalse(roleId)
                .stream()
                .map(SysRolePermission::getPermissionId)
                .collect(java.util.stream.Collectors.toSet());
    }
}
