package com.verinite.auth_service.dto;

import com.verinite.common.enums.Role;
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
    private String createdBy;
    private boolean active;
}