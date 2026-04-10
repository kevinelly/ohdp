package com.dsf.auth.security;

import com.dsf.auth.service.rbac.RbacAccessService;
import com.dsf.common.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * @author kevin
 * @description 接口权限拦截器
 * @unit Unit 作用说明：
 *       1) unit 表示“最小业务单元”或“计量单位”；
 *       2) 在本模块中用于统一数据口径，避免前后端对同一字段理解不一致；
 *       3) 典型值示例：kg（重量）、box（箱）、item（件）。
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "dsf.auth.enabled", havingValue = "true", matchIfMissing = true)
public class PermissionAuthInterceptor implements HandlerInterceptor {

    private final RbacAccessService rbacAccessService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        AuthenticatedUser user = AuthContext.get();
        if (user == null) {
            throw new BusinessException("missing auth context");
        }
        if (!rbacAccessService.isRequestAllowed(user, request.getMethod(), request.getRequestURI())) {
            throw new BusinessException("permission denied");
        }
        return true;
    }
}
