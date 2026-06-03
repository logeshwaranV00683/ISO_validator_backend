package com.verinite.rules.event;

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

    @Builder.Default
    private String sourceService = "rules-service";

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
        private String action;       // CREATE | UPDATE | DELETE | RULE_IMPORT | RULE_EXPORT
        private String entityType;   // RULE | FIELD_DEFINITION
        private Long   entityId;
        private String entityName;
        private String beforeValue;  // JSON snapshot
        private String afterValue;   // JSON snapshot
        private String description;
        private String ipAddress;
        private String correlationId;
    }
}