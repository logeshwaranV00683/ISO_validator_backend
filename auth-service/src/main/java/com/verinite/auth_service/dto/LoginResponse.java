package com.verinite.auth_service.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class LoginResponse {
    private String        token;
    private LocalDateTime expiresAt;
    private UserInfo      user;

    @Data
    @Builder
    public static class UserInfo {
        private Long   userId;
        private String username;
        private String fullName;
        private String email;
        private String avatarInitials;
        private String role;
    }
}