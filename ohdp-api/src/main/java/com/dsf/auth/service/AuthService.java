package com.dsf.auth.service;

import com.dsf.auth.dto.LoginRequest;
import com.dsf.auth.dto.LoginResponse;
import com.dsf.auth.entity.SysUser;
import com.dsf.auth.entity.SysUserToken;
import com.dsf.auth.entity.UserRole;
import com.dsf.auth.repository.SysUserRepository;
import com.dsf.auth.repository.SysUserTokenRepository;
import com.dsf.auth.security.AuthenticatedUser;
import com.dsf.common.exception.BusinessException;
import com.dsf.common.model.RecordStatus;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final SysUserRepository sysUserRepository;
    private final SysUserTokenRepository sysUserTokenRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${dsf.auth.token-hours:12}")
    private int tokenHours;

    @Transactional
    public LoginResponse login(LoginRequest request) {
        SysUser user = sysUserRepository.findByUsernameAndDeletedFalse(request.getUsername())
                .orElseThrow(() -> new BusinessException("invalid username or password"));

        if (user.getStatus() != RecordStatus.ACTIVE) {
            throw new BusinessException("user is inactive");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BusinessException("invalid username or password");
        }

        String rawToken = generateRawToken();
        String tokenHash = hashToken(rawToken);

        LocalDateTime now = LocalDateTime.now();
        SysUserToken token = new SysUserToken();
        token.setUserId(user.getId());
        token.setTokenHash(tokenHash);
        token.setClientType(request.getClientType());
        token.setIssuedAt(now);
        token.setExpiresAt(now.plusHours(tokenHours));
        token.setRevokedAt(null);
        token.setLastAccessAt(now);
        sysUserTokenRepository.save(token);

        return LoginResponse.builder()
                .token(rawToken)
                .tokenType("Bearer")
                .expiresAt(token.getExpiresAt().toString())
                .userId(user.getId())
                .username(user.getUsername())
                .role(user.getRole().name())
                .mustChangePassword(Boolean.TRUE.equals(user.getMustChangePassword()))
                .build();
    }

    @Transactional
    public void logout(String rawToken) {
        String tokenHash = hashToken(rawToken);
        SysUserToken token = sysUserTokenRepository.findByTokenHashAndDeletedFalse(tokenHash)
                .orElseThrow(() -> new BusinessException("invalid token"));
        token.setRevokedAt(LocalDateTime.now());
        token.setDeleted(true);
        sysUserTokenRepository.save(token);
    }

    @Transactional(readOnly = true)
    public AuthenticatedUser validateToken(String rawToken) {
        String tokenHash = hashToken(rawToken);
        SysUserToken token = sysUserTokenRepository.findByTokenHashAndDeletedFalse(tokenHash)
                .orElseThrow(() -> new BusinessException("invalid token"));

        if (token.getRevokedAt() != null) {
            throw new BusinessException("token is revoked");
        }
        if (token.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException("token expired");
        }

        SysUser user = sysUserRepository.findByIdAndDeletedFalse(token.getUserId())
                .orElseThrow(() -> new BusinessException("user not found"));
        if (user.getStatus() != RecordStatus.ACTIVE) {
            throw new BusinessException("user is inactive");
        }

        return AuthenticatedUser.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .role(user.getRole())
                .tokenHash(tokenHash)
                .build();
    }

    @Transactional
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        SysUser user = sysUserRepository.findByIdAndDeletedFalse(userId)
                .orElseThrow(() -> new BusinessException("user not found"));

        if (!passwordEncoder.matches(oldPassword, user.getPasswordHash())) {
            throw new BusinessException("old password mismatch");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setMustChangePassword(false);
        sysUserRepository.save(user);
        revokeAllTokens(userId);
    }

    @Transactional
    public void adminResetPassword(Long operatorUserId, Long targetUserId, String initialPassword) {
        SysUser operator = sysUserRepository.findByIdAndDeletedFalse(operatorUserId)
                .orElseThrow(() -> new BusinessException("operator not found"));
        if (operator.getRole() != UserRole.ADMIN) {
            throw new BusinessException("admin permission required");
        }

        SysUser target = sysUserRepository.findByIdAndDeletedFalse(targetUserId)
                .orElseThrow(() -> new BusinessException("target user not found"));
        target.setPasswordHash(passwordEncoder.encode(initialPassword));
        target.setMustChangePassword(true);
        sysUserRepository.save(target);
        revokeAllTokens(targetUserId);
    }

    @Transactional
    public void revokeAllTokens(Long userId) {
        List<SysUserToken> tokens = sysUserTokenRepository.findByUserIdAndDeletedFalse(userId);
        LocalDateTime now = LocalDateTime.now();
        for (SysUserToken token : tokens) {
            token.setRevokedAt(now);
            token.setDeleted(true);
        }
        sysUserTokenRepository.saveAll(tokens);
    }

    private String generateRawToken() {
        String source = UUID.randomUUID() + ":" + System.nanoTime();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(source.getBytes(StandardCharsets.UTF_8));
    }

    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("sha-256 unavailable", e);
        }
    }
}
