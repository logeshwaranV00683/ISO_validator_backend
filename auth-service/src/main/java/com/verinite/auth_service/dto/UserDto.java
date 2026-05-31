package com.verinite.auth_service.dto;

import com.verinite.auth_service.util.Role;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserDto {
    private Long id;
    private String username;
    private String fullName;
    private String email;
    private String avatarInitials;
    private Role role;
    private boolean active;
}