package com.dsf.auth.dto;

import com.dsf.auth.entity.UserRole;
import com.dsf.common.model.RecordStatus;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class UserResponse {
    Long id;
    String username;
    Long employeeId;
    UserRole role;
    RecordStatus status;
    Boolean mustChangePassword;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
}
