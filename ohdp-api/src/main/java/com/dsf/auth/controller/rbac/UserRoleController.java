package com.dsf.auth.controller.rbac;

import com.dsf.auth.dto.rbac.AssignIdsRequest;
import com.dsf.auth.service.rbac.UserRoleService;
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
 * @description 用户角色绑定接口
 * @unit Unit 作用说明：
 *       1) unit 表示“最小业务单元”或“计量单位”；
 *       2) 在本模块中用于统一数据口径，避免前后端对同一字段理解不一致；
 *       3) 典型值示例：kg（重量）、box（箱）、item（件）。
 */
@RestController
@RequestMapping("/api/system/users")
@RequiredArgsConstructor
public class UserRoleController {

    private final UserRoleService userRoleService;

    @PostMapping("/{userId}/roles")
    public void assignRoles(@PathVariable Long userId, @Valid @RequestBody AssignIdsRequest request) {
        userRoleService.assignUserRoles(userId, request);
    }

    @GetMapping("/{userId}/roles")
    public Set<Long> getRoleIds(@PathVariable Long userId) {
        return userRoleService.getRoleIdsByUserId(userId);
    }
}
