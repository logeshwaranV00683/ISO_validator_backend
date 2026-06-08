package com.verinite.history.dto.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * FIX: Every publisher (auth, rules, profile, ai) sends events with a nested
 * "payload" object, not flat fields.  Previous flat structure caused ALL audit
 * fields to be silently null in the DB.
 *
 * Published structure:
 * {
 *   "eventId":       "uuid",
 *   "eventType":     "AUDIT_EVENT",
 *   "sourceService": "auth-service",
 *   "timestamp":     "...",
 *   "payload": {
 *     "userId":      null,
 *     "username":    "admin",
 *     "action":      "LOGIN",
 *     "entityType":  "USER",
 *     ...
 *   }
 * }
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AuditLogEvent {

    private String eventId;
    private String eventType;
    private String sourceService;
    private String timestamp;
    private String correlationId;

    // The actual audit fields are nested inside "payload"
    private Payload payload;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Payload {
        private Long   userId;
        private String username;
        private String userRole;
        private String action;
        private String entityType;
        private Long   entityId;
        private String entityName;
        private String beforeValue;   // some publishers use beforeValue
        private String afterValue;    // some use afterValue
        private String oldValue;      // some use oldValue
        private String newValue;      // some use newValue
        private String description;
        private String ipAddress;
        private String correlationId;
    }
}