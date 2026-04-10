package com.dsf.auth.controller;

import com.dsf.auth.dto.AdminResetPasswordRequest;
import com.dsf.auth.dto.UserRequest;
import com.dsf.auth.dto.UserResponse;
import com.dsf.auth.security.AuthContext;
import com.dsf.auth.security.AuthenticatedUser;
import com.dsf.auth.service.AuthService;
import com.dsf.auth.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.Page;

@Tag(name = "User API")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final AuthService authService;

    @PostMapping
    public UserResponse create(@Valid @RequestBody UserRequest request) {
        return userService.create(request);
    }

    @PutMapping("/{id}")
    public UserResponse update(@PathVariable Long id, @Valid @RequestBody UserRequest request) {
        return userService.update(id, request);
    }

    @GetMapping("/{id}")
    public UserResponse findById(@PathVariable Long id) {
        return userService.findById(id);
    }

    @GetMapping
    public Page<UserResponse> findPage(
            @RequestParam(required = false) String username,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return userService.findPage(username, page, size);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        userService.delete(id);
    }

    @PostMapping("/{id}/admin-reset-password")
    public void adminResetPassword(@PathVariable Long id, @Valid @RequestBody AdminResetPasswordRequest request) {
        AuthenticatedUser operator = AuthContext.get();
        authService.adminResetPassword(operator.getUserId(), id, request.getInitialPassword());
    }
}
