package com.dsf.auth.service.rbac;

import com.dsf.auth.dto.rbac.MenuRequest;
import com.dsf.auth.dto.rbac.MenuResponse;
import com.dsf.auth.entity.PermissionType;
import com.dsf.auth.entity.SysPermission;
import com.dsf.auth.repository.SysPermissionRepository;
import com.dsf.common.exception.BusinessException;
import com.dsf.common.model.RecordStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author kevin
 * @description 菜单服务
 * @unit Unit 作用说明：
 *       1) unit 表示“最小业务单元”或“计量单位”；
 *       2) 在本模块中用于统一数据口径，避免前后端对同一字段理解不一致；
 *       3) 典型值示例：kg（重量）、box（箱）、item（件）。
 */
@Service
@RequiredArgsConstructor
public class MenuService {

    private final SysPermissionRepository sysPermissionRepository;

    @Transactional
    public MenuResponse create(MenuRequest request) {
        if (sysPermissionRepository.existsByPermCodeAndDeletedFalse(request.getPermCode())) {
            throw new BusinessException("menu code already exists");
        }
        validateParentMenu(request.getParentId());
        SysPermission menu = new SysPermission();
        apply(menu, request);
        menu.setPermType(PermissionType.MENU);
        menu.setApiMethod(null);
        menu.setApiPath(null);
        return toResponse(sysPermissionRepository.save(menu), List.of());
    }

    @Transactional
    public MenuResponse update(Long id, MenuRequest request) {
        SysPermission menu = getActiveMenu(id);
        if (sysPermissionRepository.existsByPermCodeAndDeletedFalseAndIdNot(request.getPermCode(), id)) {
            throw new BusinessException("menu code already exists");
        }
        validateParentMenu(request.getParentId());
        if (request.getParentId() != null && request.getParentId().equals(id)) {
            throw new BusinessException("menu parent cannot be self");
        }
        apply(menu, request);
        return toResponse(sysPermissionRepository.save(menu), List.of());
    }

    @Transactional(readOnly = true)
    public MenuResponse findById(Long id) {
        return toResponse(getActiveMenu(id), List.of());
    }

    @Transactional(readOnly = true)
    public List<MenuResponse> tree() {
        List<SysPermission> menus = sysPermissionRepository.findByDeletedFalseAndStatusAndPermTypeInOrderBySortNoAscIdAsc(
                RecordStatus.ACTIVE, List.of(PermissionType.MENU));
        return buildTree(menus);
    }

    @Transactional
    public void delete(Long id) {
        SysPermission menu = getActiveMenu(id);
        menu.setDeleted(true);
        sysPermissionRepository.save(menu);
    }

    private void validateParentMenu(Long parentId) {
        if (parentId == null) {
            return;
        }
        SysPermission parent = getActiveMenu(parentId);
        if (parent.getPermType() != PermissionType.MENU) {
            throw new BusinessException("menu parent must be menu type");
        }
    }

    private SysPermission getActiveMenu(Long id) {
        SysPermission permission = sysPermissionRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BusinessException("menu not found"));
        if (permission.getPermType() != PermissionType.MENU) {
            throw new BusinessException("permission is not menu type");
        }
        return permission;
    }

    private void apply(SysPermission menu, MenuRequest request) {
        menu.setPermCode(request.getPermCode());
        menu.setPermName(request.getPermName());
        menu.setParentId(request.getParentId());
        menu.setSortNo(request.getSortNo());
        menu.setStatus(request.getStatus());
    }

    private List<MenuResponse> buildTree(List<SysPermission> menus) {
        Map<Long, MenuResponse> nodeMap = menus.stream()
                .map(item -> toResponse(item, List.of()))
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
                .map(root -> resolveNode(root, nodeMap))
                .toList();
    }

    private MenuResponse resolveNode(MenuResponse node, Map<Long, MenuResponse> nodeMap) {
        List<MenuResponse> children = nodeMap.values().stream()
                .filter(item -> item.getParentId() != null && item.getParentId().equals(node.getId()))
                .sorted((a, b) -> {
                    int sortA = a.getSortNo() == null ? Integer.MAX_VALUE : a.getSortNo();
                    int sortB = b.getSortNo() == null ? Integer.MAX_VALUE : b.getSortNo();
                    if (sortA != sortB) {
                        return Integer.compare(sortA, sortB);
                    }
                    return Long.compare(a.getId(), b.getId());
                })
                .map(child -> resolveNode(child, nodeMap))
                .toList();
        return MenuResponse.builder()
                .id(node.getId())
                .permCode(node.getPermCode())
                .permName(node.getPermName())
                .parentId(node.getParentId())
                .sortNo(node.getSortNo())
                .status(node.getStatus())
                .createdAt(node.getCreatedAt())
                .updatedAt(node.getUpdatedAt())
                .children(new ArrayList<>(children))
                .build();
    }

    private MenuResponse toResponse(SysPermission menu, List<MenuResponse> children) {
        return MenuResponse.builder()
                .id(menu.getId())
                .permCode(menu.getPermCode())
                .permName(menu.getPermName())
                .parentId(menu.getParentId())
                .sortNo(menu.getSortNo())
                .status(menu.getStatus())
                .createdAt(menu.getCreatedAt())
                .updatedAt(menu.getUpdatedAt())
                .children(children)
                .build();
    }
}
