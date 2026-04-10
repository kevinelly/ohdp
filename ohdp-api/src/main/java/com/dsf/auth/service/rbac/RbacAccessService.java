package com.dsf.auth.service.rbac;

import com.dsf.auth.entity.PermissionType;
import com.dsf.auth.entity.SysPermission;
import com.dsf.auth.entity.SysRole;
import com.dsf.auth.entity.SysRolePermission;
import com.dsf.auth.entity.SysUserRole;
import com.dsf.auth.entity.UserRole;
import com.dsf.auth.repository.SysPermissionRepository;
import com.dsf.auth.repository.SysRolePermissionRepository;
import com.dsf.auth.repository.SysRoleRepository;
import com.dsf.auth.repository.SysUserRoleRepository;
import com.dsf.auth.security.AuthenticatedUser;
import com.dsf.common.model.RecordStatus;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.AntPathMatcher;

/**
 * @author kevin
 * @description RBAC 访问控制服务
 * @unit Unit 作用说明：
 *       1) unit 表示“最小业务单元”或“计量单位”；
 *       2) 在本模块中用于统一数据口径，避免前后端对同一字段理解不一致；
 *       3) 典型值示例：kg（重量）、box（箱）、item（件）。
 */
@Service
@RequiredArgsConstructor
public class RbacAccessService {

    private final SysUserRoleRepository sysUserRoleRepository;
    private final SysRoleRepository sysRoleRepository;
    private final SysRolePermissionRepository sysRolePermissionRepository;
    private final SysPermissionRepository sysPermissionRepository;

    private final AntPathMatcher antPathMatcher = new AntPathMatcher();

    @Transactional(readOnly = true)
    public boolean isRequestAllowed(AuthenticatedUser user, String method, String path) {
        if (user == null) {
            return false;
        }
        if (user.getRole() == UserRole.ADMIN) {
            return true;
        }

        List<SysPermission> managedApis = sysPermissionRepository
                .findByDeletedFalseAndStatusAndPermType(RecordStatus.ACTIVE, PermissionType.API)
                .stream()
                .filter(item -> apiMatches(item, method, path))
                .toList();

        if (managedApis.isEmpty()) {
            return true;
        }

        Set<Long> roleIds = getActiveRoleIds(user.getUserId());
        if (roleIds.isEmpty()) {
            return false;
        }
        Set<Long> permissionIds = getActivePermissionIdsByRoleIds(roleIds);
        if (permissionIds.isEmpty()) {
            return false;
        }

        return managedApis.stream().map(SysPermission::getId).anyMatch(permissionIds::contains);
    }

    @Transactional(readOnly = true)
    public Set<Long> getActiveRoleIds(Long userId) {
        List<SysUserRole> links = sysUserRoleRepository.findByUserIdAndDeletedFalse(userId);
        Set<Long> roleIds = links.stream().map(SysUserRole::getRoleId).collect(Collectors.toSet());
        if (roleIds.isEmpty()) {
            return Set.of();
        }
        return sysRoleRepository.findByIdInAndDeletedFalse(roleIds)
                .stream()
                .filter(role -> role.getStatus() == RecordStatus.ACTIVE)
                .map(SysRole::getId)
                .collect(Collectors.toSet());
    }

    @Transactional(readOnly = true)
    public Set<Long> getActivePermissionIdsByRoleIds(Collection<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return Set.of();
        }
        List<SysRolePermission> links = sysRolePermissionRepository.findByRoleIdInAndDeletedFalse(roleIds);
        Set<Long> permissionIds = links.stream().map(SysRolePermission::getPermissionId).collect(Collectors.toSet());
        if (permissionIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(sysPermissionRepository.findByIdInAndDeletedFalse(permissionIds)
                .stream()
                .filter(permission -> permission.getStatus() == RecordStatus.ACTIVE)
                .map(SysPermission::getId)
                .toList());
    }

    private boolean apiMatches(SysPermission permission, String method, String path) {
        if (permission.getApiMethod() == null || permission.getApiPath() == null) {
            return false;
        }
        String targetMethod = method == null ? "" : method.toUpperCase(Locale.ROOT);
        if (!permission.getApiMethod().equalsIgnoreCase(targetMethod)) {
            return false;
        }
        return antPathMatcher.match(permission.getApiPath(), path);
    }
}
