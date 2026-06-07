package com.verinite.auth_service.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class SessionDto {
    private Long          id;
    private String        jti;
    private String        ipAddress;
    private String        userAgent;
    private LocalDateTime issuedAt;
    private LocalDateTime expiresAt;
    private LocalDateTime revokedAt;
    private String        revokeReason;
    private Boolean       isActive;
}