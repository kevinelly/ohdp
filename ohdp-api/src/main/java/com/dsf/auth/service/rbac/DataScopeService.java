package com.dsf.auth.service.rbac;

import com.dsf.auth.entity.DataScopeType;
import com.dsf.auth.entity.SysRoleDataScope;
import com.dsf.auth.entity.UserRole;
import com.dsf.auth.repository.SysRoleDataScopeRepository;
import com.dsf.auth.security.AuthContext;
import com.dsf.auth.security.AuthenticatedUser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author kevin
 * @description 数据范围服务
 * @unit Unit 作用说明：
 *       1) unit 表示“最小业务单元”或“计量单位”；
 *       2) 在本模块中用于统一数据口径，避免前后端对同一字段理解不一致；
 *       3) 典型值示例：kg（重量）、box（箱）、item（件）。
 */
@Service
@RequiredArgsConstructor
public class DataScopeService {

    private final SysRoleDataScopeRepository sysRoleDataScopeRepository;
    private final RbacAccessService rbacAccessService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public DataScopeDecision resolveCurrentUserScope() {
        AuthenticatedUser currentUser = AuthContext.get();
        if (currentUser == null) {
            return DataScopeDecision.all();
        }
        if (currentUser.getRole() == UserRole.ADMIN) {
            return DataScopeDecision.all();
        }

        Set<Long> roleIds = rbacAccessService.getActiveRoleIds(currentUser.getUserId());
        if (roleIds.isEmpty()) {
            return DataScopeDecision.all();
        }

        List<SysRoleDataScope> dataScopes = sysRoleDataScopeRepository.findByRoleIdInAndDeletedFalse(roleIds);
        if (dataScopes.isEmpty()) {
            return DataScopeDecision.all();
        }

        Set<Long> allowedStoreIds = new HashSet<>();
        for (SysRoleDataScope dataScope : dataScopes) {
            if (dataScope.getScopeType() == DataScopeType.ALL || dataScope.getScopeType() == DataScopeType.HEADQUARTERS) {
                return DataScopeDecision.all();
            }
            if (dataScope.getScopeType() == DataScopeType.STORE_SELF) {
                return DataScopeDecision.all();
            }
            if (dataScope.getScopeType() == DataScopeType.STORE_IDS) {
                allowedStoreIds.addAll(parseStoreIds(dataScope.getStoreIdsJson()));
            }
        }

        return allowedStoreIds.isEmpty() ? DataScopeDecision.all() : DataScopeDecision.stores(allowedStoreIds);
    }

    @Transactional(readOnly = true)
    public boolean canAccessStore(Long storeId) {
        DataScopeDecision decision = resolveCurrentUserScope();
        return decision.isAllAccess() || decision.getStoreIds().contains(storeId);
    }

    private Set<Long> parseStoreIds(String json) {
        if (json == null || json.isBlank()) {
            return Set.of();
        }
        try {
            List<Long> ids = objectMapper.readValue(json, new TypeReference<>() {
            });
            return new HashSet<>(ids);
        } catch (Exception e) {
            return Set.of();
        }
    }
}
