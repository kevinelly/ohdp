package com.dsf.common.config;

import com.dsf.common.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Component
@Profile("desktop")
public class DesktopMaintenanceGate implements WebMvcConfigurer {
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock(true);

    public Scope maintenance() {
        try {
            if (!lock.writeLock().tryLock(30, TimeUnit.SECONDS)) throw new BusinessException("业务操作尚未结束，请稍后再操作测试数据。");
            return new Scope();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BusinessException("操作已中断。");
        }
    }

    public final class Scope implements AutoCloseable {
        @Override public void close() { lock.writeLock().unlock(); }
    }

    @Override public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) throws Exception {
                if (lock.readLock().tryLock()) { req.setAttribute("desktop-read-lock", true); return true; }
                res.setStatus(503);
                res.setContentType("application/json;charset=UTF-8");
                res.getWriter().write("{\"message\":\"正在导入或删除测试数据，请稍后重试。\"}");
                return false;
            }
            @Override public void afterCompletion(HttpServletRequest req, HttpServletResponse res, Object handler, Exception ex) {
                if (Boolean.TRUE.equals(req.getAttribute("desktop-read-lock"))) lock.readLock().unlock();
            }
        }).addPathPatterns("/api/**").order(-100);
    }
}
