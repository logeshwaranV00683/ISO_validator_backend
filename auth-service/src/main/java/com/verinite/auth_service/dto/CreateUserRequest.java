package com.verinite.auth_service.dto;

import com.verinite.common.enums.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateUserRequest {

    @NotBlank(message = "Username is required")
    private String username;

    @NotBlank(message = "Password is required")
    private String password;

    @NotBlank(message = "Full Name is required")
    private String fullName;

    @NotNull(message = "Role is required")
    private Role role;

    @NotNull(message = "email is required")
    @Email
    private String email;
}
