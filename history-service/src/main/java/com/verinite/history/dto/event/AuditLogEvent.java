package com.verinite.history.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogEvent {

    private String eventId;
    private String eventType;
    private String sourceService;
    private String correlationId;
    private LocalDateTime timestamp;

    // Audit payload
    private Long userId;
    private String username;
    private String userRole;
    private String action;
    private String entityType;
    private Long entityId;
    private String entityName;
    private String beforeValue;
    private String afterValue;
    private String description;
    private String ipAddress;
}
