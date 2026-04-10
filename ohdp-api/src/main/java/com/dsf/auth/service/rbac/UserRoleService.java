package com.dsf.auth.service.rbac;

import com.dsf.auth.dto.rbac.AssignIdsRequest;
import com.dsf.auth.entity.SysRole;
import com.dsf.auth.entity.SysUser;
import com.dsf.auth.entity.SysUserRole;
import com.dsf.auth.repository.SysRoleRepository;
import com.dsf.auth.repository.SysUserRepository;
import com.dsf.auth.repository.SysUserRoleRepository;
import com.dsf.common.exception.BusinessException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author kevin
 * @description 用户角色绑定服务
 * @unit Unit 作用说明：
 *       1) unit 表示“最小业务单元”或“计量单位”；
 *       2) 在本模块中用于统一数据口径，避免前后端对同一字段理解不一致；
 *       3) 典型值示例：kg（重量）、box（箱）、item（件）。
 */
@Service
@RequiredArgsConstructor
public class UserRoleService {

    private final SysUserRepository sysUserRepository;
    private final SysRoleRepository sysRoleRepository;
    private final SysUserRoleRepository sysUserRoleRepository;

    @Transactional
    public void assignUserRoles(Long userId, AssignIdsRequest request) {
        SysUser user = sysUserRepository.findByIdAndDeletedFalse(userId)
                .orElseThrow(() -> new BusinessException("user not found"));

        List<Long> ids = request.getIds() == null ? List.of() : request.getIds();
        List<SysRole> roles = ids.isEmpty() ? List.of() : sysRoleRepository.findByIdInAndDeletedFalse(ids);
        if (roles.size() != new HashSet<>(ids).size()) {
            throw new BusinessException("role contains invalid id");
        }

        List<SysUserRole> existed = sysUserRoleRepository.findByUserIdAndDeletedFalse(user.getId());
        for (SysUserRole item : existed) {
            item.setDeleted(true);
        }
        sysUserRoleRepository.saveAll(existed);

        Set<Long> dedupIds = new HashSet<>(ids);
        for (Long roleId : dedupIds) {
            SysUserRole relation = new SysUserRole();
            relation.setUserId(user.getId());
            relation.setRoleId(roleId);
            relation.setDeleted(false);
            sysUserRoleRepository.save(relation);
        }
    }

    @Transactional(readOnly = true)
    public Set<Long> getRoleIdsByUserId(Long userId) {
        sysUserRepository.findByIdAndDeletedFalse(userId)
                .orElseThrow(() -> new BusinessException("user not found"));
        return sysUserRoleRepository.findByUserIdAndDeletedFalse(userId)
                .stream()
                .map(SysUserRole::getRoleId)
                .collect(java.util.stream.Collectors.toSet());
    }
}
