package com.dsf.auth.controller.rbac;

import com.dsf.auth.dto.rbac.MenuResponse;
import com.dsf.auth.service.rbac.CurrentUserPermissionService;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author kevin
 * @description 当前用户权限接口
 * @unit Unit 作用说明：
 *       1) unit 表示“最小业务单元”或“计量单位”；
 *       2) 在本模块中用于统一数据口径，避免前后端对同一字段理解不一致；
 *       3) 典型值示例：kg（重量）、box（箱）、item（件）。
 */
@RestController
@RequestMapping("/api/system/current-user")
@RequiredArgsConstructor
public class CurrentUserPermissionController {

    private final CurrentUserPermissionService currentUserPermissionService;

    @GetMapping("/menus")
    public List<MenuResponse> currentMenus() {
        return currentUserPermissionService.currentUserMenus();
    }

    @GetMapping("/permission-codes")
    public Set<String> permissionCodes() {
        return currentUserPermissionService.currentUserPermissionCodes();
    }
}

