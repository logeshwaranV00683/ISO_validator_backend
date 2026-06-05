package com.verinite.history.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogResponse {

    private Long auditId;
    private Long userId;
    private String username;
    private String userRole;
    private String sourceService;
    private String action;
    private String entityType;
    private Long entityId;
    private String entityName;
    private String beforeValue;
    private String afterValue;
    private String description;
    private String ipAddress;
    private String correlationId;
    private LocalDateTime createdAt;
}