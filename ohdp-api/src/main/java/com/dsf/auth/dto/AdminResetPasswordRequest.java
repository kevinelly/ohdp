package com.dsf.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AdminResetPasswordRequest {

    @NotBlank
    private String initialPassword;
}
