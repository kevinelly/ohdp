package com.dsf.auth.security;

import com.dsf.auth.entity.UserRole;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AuthenticatedUser {
    Long userId;
    String username;
    UserRole role;
    String tokenHash;
}
