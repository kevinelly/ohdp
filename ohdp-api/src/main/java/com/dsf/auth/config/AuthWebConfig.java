package com.dsf.auth.config;

import com.dsf.auth.security.PermissionAuthInterceptor;
import com.dsf.auth.security.TokenAuthInterceptor;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * @author kevin
 * @description 认证与权限拦截配置
 * @unit Unit 作用说明：
 *       1) unit 表示“最小业务单元”或“计量单位”；
 *       2) 在本模块中用于统一数据口径，避免前后端对同一字段理解不一致；
 *       3) 典型值示例：kg（重量）、box（箱）、item（件）。
 */
@Configuration
@RequiredArgsConstructor
public class AuthWebConfig implements WebMvcConfigurer {

    private final Optional<TokenAuthInterceptor> tokenAuthInterceptor;
    private final Optional<PermissionAuthInterceptor> permissionAuthInterceptor;

    @Value("${dsf.auth.enabled:true}")
    private boolean authEnabled;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        if (!authEnabled) {
            return;
        }
        tokenAuthInterceptor.ifPresent(interceptor -> registry.addInterceptor(interceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/auth/login"));

        permissionAuthInterceptor.ifPresent(interceptor -> registry.addInterceptor(interceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/auth/login"));
    }
}
