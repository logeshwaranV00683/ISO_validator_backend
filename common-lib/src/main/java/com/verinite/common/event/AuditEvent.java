package com.verinite.common.event;

import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditEvent {

    @Builder.Default
    private String eventId = UUID.randomUUID().toString();

    @Builder.Default
    private String eventType = "AUDIT_EVENT";

    private String sourceService;

    @Builder.Default
    private String timestamp = Instant.now().toString();

    private Payload payload;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Payload {
        private Long   userId;
        private String username;
        private String userRole;
        private String action;
        private String entityType;
        private Long   entityId;
        private String entityName;
        private String beforeValue;
        private String afterValue;
        private String description;
        private String ipAddress;
        private String correlationId;
    }
}