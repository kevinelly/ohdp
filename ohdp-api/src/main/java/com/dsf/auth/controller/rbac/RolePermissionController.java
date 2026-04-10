package com.dsf.auth.controller.rbac;

import com.dsf.auth.dto.rbac.AssignIdsRequest;
import com.dsf.auth.service.rbac.RolePermissionService;
import jakarta.validation.Valid;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author kevin
 * @description 角色权限绑定接口
 * @unit Unit 作用说明：
 *       1) unit 表示“最小业务单元”或“计量单位”；
 *       2) 在本模块中用于统一数据口径，避免前后端对同一字段理解不一致；
 *       3) 典型值示例：kg（重量）、box（箱）、item（件）。
 */
@RestController
@RequestMapping("/api/system/roles")
@RequiredArgsConstructor
public class RolePermissionController {

    private final RolePermissionService rolePermissionService;

    @PostMapping("/{roleId}/permissions")
    public void assignPermissions(@PathVariable Long roleId, @Valid @RequestBody AssignIdsRequest request) {
        rolePermissionService.assignRolePermissions(roleId, request);
    }

    @GetMapping("/{roleId}/permissions")
    public Set<Long> getPermissionIds(@PathVariable Long roleId) {
        return rolePermissionService.getPermissionIdsByRoleId(roleId);
    }
}
