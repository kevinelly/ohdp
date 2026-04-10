package com.dsf.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import java.util.Map;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author kevin
 * @description Swagger 中文增强说明
 * @unit Unit 作用说明：
 *       1) unit 表示“最小业务单元”或“计量单位”；
 *       2) 在本模块中用于统一数据口径，避免前后端对同一字段理解不一致；
 *       3) 典型值示例：kg（重量）、box（箱）、item（件）。
 */
@Configuration
public class OpenApiChineseCustomizer {

    @Bean
    public OpenApiCustomizer chineseDocsCustomizer() {
        return this::customizeOperations;
    }

    private void customizeOperations(OpenAPI openApi) {
        if (openApi.getPaths() == null) {
            return;
        }

        Map<String, String> summaries = Map.ofEntries(
                Map.entry("POST /api/auth/login", "用户登录"),
                Map.entry("POST /api/auth/logout", "用户登出"),
                Map.entry("POST /api/auth/change-password", "用户修改密码"),
                Map.entry("POST /api/users/{id}/admin-reset-password", "管理员重置密码"),
                Map.entry("POST /api/system/roles", "新增角色"),
                Map.entry("GET /api/system/roles", "分页查询角色"),
                Map.entry("POST /api/system/permissions", "新增权限"),
                Map.entry("GET /api/system/permissions", "分页查询权限"),
                Map.entry("POST /api/system/users/{userId}/roles", "用户绑定角色"),
                Map.entry("POST /api/system/roles/{roleId}/permissions", "角色绑定权限"),
                Map.entry("POST /api/system/roles/{roleId}/data-scope", "配置角色数据范围")
        );

        openApi.getPaths().forEach((path, pathItem) -> {
            if (pathItem.readOperationsMap() == null) {
                return;
            }
            pathItem.readOperationsMap().forEach((method, operation) -> {
                String key = method.name() + " " + path;
                String summary = summaries.get(key);
                if (summary != null) {
                    operation.setSummary(summary);
                }
                applyDefaultDescription(path, operation);
            });
        });
    }

    private void applyDefaultDescription(String path, Operation operation) {
        if (operation.getDescription() != null && !operation.getDescription().isBlank()) {
            return;
        }
        if ("/api/auth/login".equals(path)) {
            operation.setDescription("登录接口，无需 Token，请求体包含 username、password、clientType。");
            return;
        }
        operation.setDescription("受保护接口，请在 Header 中携带 Authorization: Bearer <token>。");
    }
}
