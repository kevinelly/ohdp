package com.dsf.auth.service.rbac;

import com.dsf.auth.dto.rbac.RoleDataScopeRequest;
import com.dsf.auth.dto.rbac.RoleDataScopeResponse;
import com.dsf.auth.entity.DataScopeType;
import com.dsf.auth.entity.SysRole;
import com.dsf.auth.entity.SysRoleDataScope;
import com.dsf.auth.repository.SysRoleDataScopeRepository;
import com.dsf.auth.repository.SysRoleRepository;
import com.dsf.common.exception.BusinessException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author kevin
 * @description 角色数据范围服务
 * @unit Unit 作用说明�? *       1) unit 表示“最小业务单元”或“计量单位”；
 *       2) 在本模块中用于统一数据口径，避免前后端对同一字段理解不一致；
 *       3) 典型值示例：kg（重量）、box（箱）、item（件）�? */
@Service
@RequiredArgsConstructor
public class RoleDataScopeService {

    private final SysRoleRepository sysRoleRepository;
    private final SysRoleDataScopeRepository sysRoleDataScopeRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public RoleDataScopeResponse save(Long roleId, RoleDataScopeRequest request) {
        SysRole role = sysRoleRepository.findByIdAndDeletedFalse(roleId)
                .orElseThrow(() -> new BusinessException("role not found"));
        SysRoleDataScope dataScope = sysRoleDataScopeRepository.findByRoleIdAndDeletedFalse(role.getId())
                .orElseGet(SysRoleDataScope::new);

        dataScope.setRoleId(role.getId());
        dataScope.setScopeType(request.getScopeType());
        dataScope.setStoreIdsJson(toJsonStoreIds(request.getScopeType(), request.getStoreIds()));
        dataScope.setDeleted(false);
        return toResponse(sysRoleDataScopeRepository.save(dataScope));
    }

    @Transactional(readOnly = true)
    public RoleDataScopeResponse getByRoleId(Long roleId) {
        sysRoleRepository.findByIdAndDeletedFalse(roleId)
                .orElseThrow(() -> new BusinessException("role not found"));
        SysRoleDataScope dataScope = sysRoleDataScopeRepository.findByRoleIdAndDeletedFalse(roleId)
                .orElseThrow(() -> new BusinessException("role data scope not configured"));
        return toResponse(dataScope);
    }

    private String toJsonStoreIds(DataScopeType scopeType, List<Long> storeIds) {
        if (scopeType != DataScopeType.STORE_IDS) {
            return null;
        }
        List<Long> ids = storeIds == null ? List.of() : new ArrayList<>(storeIds);
        try {
            return objectMapper.writeValueAsString(ids);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialize store ids", e);
        }
    }

    private RoleDataScopeResponse toResponse(SysRoleDataScope dataScope) {
        return RoleDataScopeResponse.builder()
                .roleId(dataScope.getRoleId())
                .scopeType(dataScope.getScopeType())
                .storeIds(parseStoreIds(dataScope.getStoreIdsJson()))
                .build();
    }

    private List<Long> parseStoreIds(String storeIdsJson) {
        if (storeIdsJson == null || storeIdsJson.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(storeIdsJson, new TypeReference<>() {
            });
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }
}
