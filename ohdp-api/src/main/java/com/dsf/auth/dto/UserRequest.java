package com.dsf.auth.dto;

import com.dsf.auth.entity.UserRole;
import com.dsf.common.model.RecordStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UserRequest {

    @NotBlank
    @Size(max = 64)
    private String username;

    private String password;

    private Long employeeId;

    @NotNull
    private UserRole role;

    @NotNull
    private RecordStatus status;
}
