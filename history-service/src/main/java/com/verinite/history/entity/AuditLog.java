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
    @Column(name = "id")                        // schema: id (not audit_id)
    private Long id;

    @Column(name = "action", nullable = false, length = 100)
    private String action;

    @Column(name = "entity_type", length = 50)
    private String entityType;

    @Column(name = "entity_id", length = 50)    // schema: VARCHAR(50), not BIGINT
    private String entityId;

    @Column(name = "entity_name", length = 200)
    private String entityName;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "username_snapshot", length = 50)  // schema: username_snapshot (not username)
    private String usernameSnapshot;

    @Column(name = "user_role", length = 20)
    private String userRole;

    @Column(name = "source_service", length = 50)
    private String sourceService;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "correlation_id", length = 36)
    private String correlationId;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "old_value", columnDefinition = "JSON")   // schema: old_value (not before_value)
    private String oldValue;

    @Column(name = "new_value", columnDefinition = "JSON")   // schema: new_value (not after_value)
    private String newValue;

    // INSERT ONLY — never updated
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}