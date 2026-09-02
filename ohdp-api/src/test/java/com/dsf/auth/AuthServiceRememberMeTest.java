package com.dsf.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.dsf.auth.dto.LoginRequest;
import com.dsf.auth.dto.LoginResponse;
import com.dsf.auth.entity.SysUser;
import com.dsf.auth.entity.SysUserToken;
import com.dsf.auth.entity.UserRole;
import com.dsf.auth.repository.SysUserRepository;
import com.dsf.auth.repository.SysUserTokenRepository;
import com.dsf.auth.service.AuthService;
import com.dsf.common.model.RecordStatus;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AuthServiceRememberMeTest {

    @Mock private SysUserRepository userRepository;
    @Mock private SysUserTokenRepository tokenRepository;
    @Mock private PasswordEncoder passwordEncoder;

    private AuthService service;

    @BeforeEach
    void setUp() {
        service = new AuthService(userRepository, tokenRepository, passwordEncoder);
        ReflectionTestUtils.setField(service, "tokenHours", 12);
        ReflectionTestUtils.setField(service, "rememberTokenHours", 720);

        SysUser user = new SysUser();
        user.setId(1L);
        user.setUsername("admin");
        user.setPasswordHash("encoded");
        user.setRole(UserRole.ADMIN);
        user.setStatus(RecordStatus.ACTIVE);
        user.setMustChangePassword(false);
        user.setDeleted(false);
        when(userRepository.findByUsernameAndDeletedFalse("admin")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret", "encoded")).thenReturn(true);
        when(tokenRepository.save(any(SysUserToken.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void rememberedLoginExpiresAfterThirtyDays() {
        LoginResponse response = service.login(request(true));
        long hours = Duration.between(LocalDateTime.now(), LocalDateTime.parse(response.getExpiresAt())).toHours();
        assertThat(hours).isBetween(719L, 720L);
    }

    @Test
    void regularLoginKeepsTwelveHourExpiry() {
        LoginResponse response = service.login(request(false));
        long hours = Duration.between(LocalDateTime.now(), LocalDateTime.parse(response.getExpiresAt())).toHours();
        assertThat(hours).isBetween(11L, 12L);
    }

    private LoginRequest request(boolean rememberMe) {
        LoginRequest request = new LoginRequest();
        request.setUsername("admin");
        request.setPassword("secret");
        request.setClientType("web");
        request.setRememberMe(rememberMe);
        return request;
    }
}
