package com.dsf.auth.controller.rbac;

import com.dsf.auth.dto.rbac.RoleDataScopeRequest;
import com.dsf.auth.dto.rbac.RoleDataScopeResponse;
import com.dsf.auth.service.rbac.RoleDataScopeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author kevin
 * @description 角色数据范围接口
 * @unit Unit 作用说明：
 *       1) unit 表示“最小业务单元”或“计量单位”；
 *       2) 在本模块中用于统一数据口径，避免前后端对同一字段理解不一致；
 *       3) 典型值示例：kg（重量）、box（箱）、item（件）。
 */
@RestController
@RequestMapping("/api/system/roles")
@RequiredArgsConstructor
public class RoleDataScopeController {

    private final RoleDataScopeService roleDataScopeService;

    @PostMapping("/{roleId}/data-scope")
    public RoleDataScopeResponse save(@PathVariable Long roleId, @Valid @RequestBody RoleDataScopeRequest request) {
        return roleDataScopeService.save(roleId, request);
    }

    @GetMapping("/{roleId}/data-scope")
    public RoleDataScopeResponse getByRoleId(@PathVariable Long roleId) {
        return roleDataScopeService.getByRoleId(roleId);
    }
}
