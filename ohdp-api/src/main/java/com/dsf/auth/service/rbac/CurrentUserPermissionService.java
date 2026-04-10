package com.dsf.auth.service.rbac;

import com.dsf.auth.dto.rbac.MenuResponse;
import com.dsf.auth.entity.PermissionType;
import com.dsf.auth.entity.SysPermission;
import com.dsf.auth.entity.UserRole;
import com.dsf.auth.repository.SysPermissionRepository;
import com.dsf.auth.security.AuthContext;
import com.dsf.auth.security.AuthenticatedUser;
import com.dsf.common.exception.BusinessException;
import com.dsf.common.model.RecordStatus;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author kevin
 * @description 当前用户权限服务
 * @unit Unit 作用说明：
 *       1) unit 表示“最小业务单元”或“计量单位”；
 *       2) 在本模块中用于统一数据口径，避免前后端对同一字段理解不一致；
 *       3) 典型值示例：kg（重量）、box（箱）、item（件）。
 */
@Service
@RequiredArgsConstructor
public class CurrentUserPermissionService {

    private final RbacAccessService rbacAccessService;
    private final SysPermissionRepository sysPermissionRepository;

    @Transactional(readOnly = true)
    public List<MenuResponse> currentUserMenus() {
        Collection<SysPermission> permissions = currentUserPermissions();
        List<SysPermission> menus = permissions.stream()
                .filter(item -> item.getPermType() == PermissionType.MENU)
                .sorted((a, b) -> {
                    int sortA = a.getSortNo() == null ? Integer.MAX_VALUE : a.getSortNo();
                    int sortB = b.getSortNo() == null ? Integer.MAX_VALUE : b.getSortNo();
                    if (sortA != sortB) {
                        return Integer.compare(sortA, sortB);
                    }
                    return Long.compare(a.getId(), b.getId());
                })
                .toList();
        return buildTree(menus);
    }

    @Transactional(readOnly = true)
    public Set<String> currentUserPermissionCodes() {
        return currentUserPermissions().stream()
                .map(SysPermission::getPermCode)
                .collect(Collectors.toSet());
    }

    private Collection<SysPermission> currentUserPermissions() {
        AuthenticatedUser user = AuthContext.get();
        if (user == null) {
            throw new BusinessException("missing auth context");
        }
        if (user.getRole() == UserRole.ADMIN) {
            return sysPermissionRepository.findByDeletedFalseAndStatusOrderBySortNoAscIdAsc(RecordStatus.ACTIVE);
        }

        Set<Long> roleIds = rbacAccessService.getActiveRoleIds(user.getUserId());
        Set<Long> permissionIds = rbacAccessService.getActivePermissionIdsByRoleIds(roleIds);
        if (permissionIds.isEmpty()) {
            return List.of();
        }
        return sysPermissionRepository.findByIdInAndDeletedFalseOrderBySortNoAscIdAsc(permissionIds)
                .stream()
                .filter(item -> item.getStatus() == RecordStatus.ACTIVE)
                .toList();
    }

    private List<MenuResponse> buildTree(List<SysPermission> menus) {
        Map<Long, MenuResponse> nodeMap = menus.stream()
                .map(this::toNode)
                .collect(Collectors.toMap(MenuResponse::getId, Function.identity()));

        return nodeMap.values().stream()
                .filter(node -> node.getParentId() == null || !nodeMap.containsKey(node.getParentId()))
                .sorted((a, b) -> {
                    int sortA = a.getSortNo() == null ? Integer.MAX_VALUE : a.getSortNo();
                    int sortB = b.getSortNo() == null ? Integer.MAX_VALUE : b.getSortNo();
                    if (sortA != sortB) {
                        return Integer.compare(sortA, sortB);
                    }
                    return Long.compare(a.getId(), b.getId());
                })
                .map(root -> fillChildren(root, nodeMap))
                .toList();
    }

    private MenuResponse fillChildren(MenuResponse parent, Map<Long, MenuResponse> nodeMap) {
        List<MenuResponse> children = nodeMap.values().stream()
                .filter(item -> item.getParentId() != null && item.getParentId().equals(parent.getId()))
                .sorted((a, b) -> {
                    int sortA = a.getSortNo() == null ? Integer.MAX_VALUE : a.getSortNo();
                    int sortB = b.getSortNo() == null ? Integer.MAX_VALUE : b.getSortNo();
                    if (sortA != sortB) {
                        return Integer.compare(sortA, sortB);
                    }
                    return Long.compare(a.getId(), b.getId());
                })
                .map(item -> fillChildren(item, nodeMap))
                .toList();

        return MenuResponse.builder()
                .id(parent.getId())
                .permCode(parent.getPermCode())
                .permName(parent.getPermName())
                .parentId(parent.getParentId())
                .sortNo(parent.getSortNo())
                .status(parent.getStatus())
                .createdAt(parent.getCreatedAt())
                .updatedAt(parent.getUpdatedAt())
                .children(new ArrayList<>(children))
                .build();
    }

    private MenuResponse toNode(SysPermission permission) {
        return MenuResponse.builder()
                .id(permission.getId())
                .permCode(permission.getPermCode())
                .permName(permission.getPermName())
                .parentId(permission.getParentId())
                .sortNo(permission.getSortNo())
                .status(permission.getStatus())
                .createdAt(permission.getCreatedAt())
                .updatedAt(permission.getUpdatedAt())
                .children(List.of())
                .build();
    }
}

