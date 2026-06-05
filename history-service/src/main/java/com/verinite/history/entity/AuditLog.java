package com.verinite.history.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "audit_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "audit_id")
    private Long auditId;

    // Cross-service ref — nullable if user deleted
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "username", length = 50)
    private String username;

    @Column(name = "user_role", length = 20)
    private String userRole;

    // e.g. "auth-service", "rules-service", "profile-service"
    @Column(name = "source_service", nullable = false, length = 50)
    private String sourceService;

    @Column(name = "action", nullable = false, length = 50)
    private String action;

    // e.g. RULE, FORMAT, PROFILE, USER, AI_CONFIG
    @Column(name = "entity_type", nullable = false, length = 50)
    private String entityType;

    @Column(name = "entity_id")
    private Long entityId;

    @Column(name = "entity_name", length = 200)
    private String entityName;

    @Column(name = "before_value", columnDefinition = "LONGTEXT")
    private String beforeValue;   // JSON snapshot before change

    @Column(name = "after_value", columnDefinition = "LONGTEXT")
    private String afterValue;    // JSON snapshot after change

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "correlation_id", length = 36)
    private String correlationId;

    // INSERT ONLY — set once at creation, never updated
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}