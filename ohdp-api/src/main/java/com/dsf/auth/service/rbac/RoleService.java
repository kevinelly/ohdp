package com.dsf.auth.service.rbac;

import com.dsf.auth.dto.rbac.RoleRequest;
import com.dsf.auth.dto.rbac.RoleResponse;
import com.dsf.auth.entity.SysRole;
import com.dsf.auth.repository.SysRoleRepository;
import com.dsf.common.exception.BusinessException;
import com.dsf.common.model.RecordStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author kevin
 * @description 角色服务
 * @unit Unit 作用说明：
 *       1) unit 表示“最小业务单元”或“计量单位”；
 *       2) 在本模块中用于统一数据口径，避免前后端对同一字段理解不一致；
 *       3) 典型值示例：kg（重量）、box（箱）、item（件）。
 */
@Service
@RequiredArgsConstructor
public class RoleService {

    private final SysRoleRepository sysRoleRepository;

    @Transactional
    public RoleResponse create(RoleRequest request) {
        if (sysRoleRepository.existsByRoleCodeAndDeletedFalse(request.getRoleCode())) {
            throw new BusinessException("role code already exists");
        }
        SysRole role = new SysRole();
        apply(role, request);
        return toResponse(sysRoleRepository.save(role));
    }

    @Transactional
    public RoleResponse update(Long id, RoleRequest request) {
        SysRole role = sysRoleRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BusinessException("role not found"));
        if (sysRoleRepository.existsByRoleCodeAndDeletedFalseAndIdNot(request.getRoleCode(), id)) {
            throw new BusinessException("role code already exists");
        }
        apply(role, request);
        return toResponse(sysRoleRepository.save(role));
    }

    @Transactional(readOnly = true)
    public RoleResponse findById(Long id) {
        return toResponse(sysRoleRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BusinessException("role not found")));
    }

    @Transactional(readOnly = true)
    public Page<RoleResponse> findPage(String roleCode, String roleName, int page, int size) {
        return sysRoleRepository.findByDeletedFalseAndRoleCodeContainingAndRoleNameContaining(
                        normalize(roleCode), normalize(roleName),
                        PageRequest.of(Math.max(0, page), Math.max(1, size), Sort.by(Sort.Direction.DESC, "id")))
                .map(this::toResponse);
    }

    @Transactional
    public void delete(Long id) {
        SysRole role = sysRoleRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BusinessException("role not found"));
        role.setDeleted(true);
        role.setStatus(RecordStatus.INACTIVE);
        sysRoleRepository.save(role);
    }

    private void apply(SysRole role, RoleRequest request) {
        role.setRoleCode(request.getRoleCode());
        role.setRoleName(request.getRoleName());
        role.setRemark(request.getRemark());
        role.setStatus(request.getStatus());
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private RoleResponse toResponse(SysRole role) {
        return RoleResponse.builder()
                .id(role.getId())
                .roleCode(role.getRoleCode())
                .roleName(role.getRoleName())
                .remark(role.getRemark())
                .status(role.getStatus())
                .createdAt(role.getCreatedAt())
                .updatedAt(role.getUpdatedAt())
                .build();
    }
}

