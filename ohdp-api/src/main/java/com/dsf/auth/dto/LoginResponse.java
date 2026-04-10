package com.dsf.auth.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class LoginResponse {
    String token;
    String tokenType;
    String expiresAt;
    Long userId;
    String username;
    String role;
    Boolean mustChangePassword;
}
