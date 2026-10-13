package com.dsf.auth.config;

import com.dsf.auth.entity.SysUser;
import com.dsf.auth.entity.UserRole;
import com.dsf.auth.repository.SysUserRepository;
import com.dsf.common.model.RecordStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(name = "dsf.auth.bootstrap-admin-enabled", havingValue = "true", matchIfMissing = true)
public class AuthBootstrapConfig {

    private final SysUserRepository sysUserRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${dsf.auth.bootstrap-admin-username:admin}")
    private String adminUsername;

    @Value("${dsf.auth.initial-password:123456}")
    private String initialPassword;

    @Value("${dsf.auth.bootstrap-must-change-password:true}")
    private boolean mustChangePassword;

    @Bean
    public CommandLineRunner initAdminUser() {
        return args -> {
            if (sysUserRepository.findByUsernameAndDeletedFalse(adminUsername).isPresent()) {
                return;
            }
            SysUser user = new SysUser();
            user.setUsername(adminUsername);
            user.setEmployeeId(null);
            user.setRole(UserRole.ADMIN);
            user.setStatus(RecordStatus.ACTIVE);
            user.setMustChangePassword(mustChangePassword);
            user.setPasswordHash(passwordEncoder.encode(initialPassword));
            sysUserRepository.save(user);
        };
    }
}
