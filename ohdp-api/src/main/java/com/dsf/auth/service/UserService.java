package com.dsf.auth.service;

import com.dsf.auth.dto.UserRequest;
import com.dsf.auth.dto.UserResponse;
import com.dsf.auth.entity.SysUser;
import com.dsf.auth.repository.SysUserRepository;
import com.dsf.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class UserService {

    private final SysUserRepository sysUserRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${dsf.auth.initial-password:123456}")
    private String initialPassword;

    @Transactional
    public UserResponse create(UserRequest request) {
        if (sysUserRepository.existsByUsernameAndDeletedFalse(request.getUsername())) {
            throw new BusinessException("username already exists");
        }
        if (request.getEmployeeId() != null && sysUserRepository.existsByEmployeeIdAndDeletedFalse(request.getEmployeeId())) {
            throw new BusinessException("employee already bound");
        }

        SysUser user = new SysUser();
        user.setUsername(request.getUsername());
        user.setEmployeeId(request.getEmployeeId());
        user.setRole(request.getRole());
        user.setStatus(request.getStatus());
        user.setMustChangePassword(true);
        String password = (request.getPassword() == null || request.getPassword().isBlank()) ? initialPassword : request.getPassword();
        user.setPasswordHash(passwordEncoder.encode(password));
        return toResponse(sysUserRepository.save(user));
    }

    @Transactional
    public UserResponse update(Long id, UserRequest request) {
        SysUser user = sysUserRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BusinessException("user not found"));

        if (!user.getUsername().equals(request.getUsername()) && sysUserRepository.existsByUsernameAndDeletedFalse(request.getUsername())) {
            throw new BusinessException("username already exists");
        }

        if (!Objects.equals(user.getEmployeeId(), request.getEmployeeId())
                && request.getEmployeeId() != null
                && sysUserRepository.existsByEmployeeIdAndDeletedFalseAndIdNot(request.getEmployeeId(), id)) {
            throw new BusinessException("employee already bound");
        }

        user.setUsername(request.getUsername());
        user.setEmployeeId(request.getEmployeeId());
        user.setRole(request.getRole());
        user.setStatus(request.getStatus());
        return toResponse(sysUserRepository.save(user));
    }

    @Transactional(readOnly = true)
    public UserResponse findById(Long id) {
        return toResponse(sysUserRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BusinessException("user not found")));
    }

    @Transactional(readOnly = true)
    public Page<UserResponse> findPage(String username, int page, int size) {
        String keyword = username == null ? "" : username.trim();
        return sysUserRepository.findByDeletedFalseAndUsernameContaining(
                        keyword,
                        PageRequest.of(Math.max(page, 0), Math.max(size, 1), Sort.by(Sort.Direction.DESC, "id")))
                .map(this::toResponse);
    }

    @Transactional
    public void delete(Long id) {
        SysUser user = sysUserRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BusinessException("user not found"));
        user.setDeleted(true);
        user.setStatus(com.dsf.common.model.RecordStatus.INACTIVE);
        sysUserRepository.save(user);
    }

    private UserResponse toResponse(SysUser user) {
        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .employeeId(user.getEmployeeId())
                .role(user.getRole())
                .status(user.getStatus())
                .mustChangePassword(user.getMustChangePassword())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}
