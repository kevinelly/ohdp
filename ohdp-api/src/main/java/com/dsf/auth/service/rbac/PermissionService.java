package com.dsf.auth.service.rbac;

import com.dsf.auth.dto.rbac.PermissionRequest;
import com.dsf.auth.dto.rbac.PermissionResponse;
import com.dsf.auth.entity.SysPermission;
import com.dsf.auth.repository.SysPermissionRepository;
import com.dsf.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author kevin
 * @description 权限服务
 * @unit Unit 作用说明：
 *       1) unit 表示“最小业务单元”或“计量单位”；
 *       2) 在本模块中用于统一数据口径，避免前后端对同一字段理解不一致；
 *       3) 典型值示例：kg（重量）、box（箱）、item（件）。
 */
@Service
@RequiredArgsConstructor
public class PermissionService {

    private final SysPermissionRepository sysPermissionRepository;

    @Transactional
    public PermissionResponse create(PermissionRequest request) {
        if (sysPermissionRepository.existsByPermCodeAndDeletedFalse(request.getPermCode())) {
            throw new BusinessException("permission code already exists");
        }
        SysPermission permission = new SysPermission();
        apply(permission, request);
        return toResponse(sysPermissionRepository.save(permission));
    }

    @Transactional
    public PermissionResponse update(Long id, PermissionRequest request) {
        SysPermission permission = sysPermissionRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BusinessException("permission not found"));
        if (sysPermissionRepository.existsByPermCodeAndDeletedFalseAndIdNot(request.getPermCode(), id)) {
            throw new BusinessException("permission code already exists");
        }
        apply(permission, request);
        return toResponse(sysPermissionRepository.save(permission));
    }

    @Transactional(readOnly = true)
    public PermissionResponse findById(Long id) {
        return toResponse(sysPermissionRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BusinessException("permission not found")));
    }

    @Transactional(readOnly = true)
    public Page<PermissionResponse> findPage(String permCode, String permName, int page, int size) {
        return sysPermissionRepository.findByDeletedFalseAndPermCodeContainingAndPermNameContaining(
                        normalize(permCode), normalize(permName),
                        PageRequest.of(Math.max(0, page), Math.max(1, size), Sort.by(Sort.Direction.ASC, "sortNo").and(Sort.by("id"))))
                .map(this::toResponse);
    }

    @Transactional
    public void delete(Long id) {
        SysPermission permission = sysPermissionRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BusinessException("permission not found"));
        permission.setDeleted(true);
        sysPermissionRepository.save(permission);
    }

    private void apply(SysPermission permission, PermissionRequest request) {
        permission.setPermCode(request.getPermCode());
        permission.setPermName(request.getPermName());
        permission.setPermType(request.getPermType());
        permission.setApiMethod(request.getApiMethod());
        permission.setApiPath(request.getApiPath());
        permission.setParentId(request.getParentId());
        permission.setSortNo(request.getSortNo());
        permission.setStatus(request.getStatus());
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private PermissionResponse toResponse(SysPermission permission) {
        return PermissionResponse.builder()
                .id(permission.getId())
                .permCode(permission.getPermCode())
                .permName(permission.getPermName())
                .permType(permission.getPermType())
                .apiMethod(permission.getApiMethod())
                .apiPath(permission.getApiPath())
                .parentId(permission.getParentId())
                .sortNo(permission.getSortNo())
                .status(permission.getStatus())
                .createdAt(permission.getCreatedAt())
                .updatedAt(permission.getUpdatedAt())
                .build();
    }
}
