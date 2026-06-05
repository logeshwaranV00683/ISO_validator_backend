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

    private Long auditId;          // maps from entity id field
    private String action;
    private String entityType;
    private String entityId;
    private String entityName;
    private Long userId;
    private String usernameSnapshot;
    private String userRole;
    private String sourceService;
    private String description;
    private String correlationId;
    private String ipAddress;
    private String oldValue;
    private String newValue;
    private LocalDateTime createdAt;
}