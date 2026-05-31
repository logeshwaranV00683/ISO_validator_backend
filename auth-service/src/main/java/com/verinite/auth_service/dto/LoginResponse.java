package com.verinite.auth_service.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class LoginResponse {
    private String token;
    private String username;
    private String role;
    private String avatarInitials;
    private LocalDateTime expiresAt;   // useful for frontend token expiry tracking
}