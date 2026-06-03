package com.verinite.profile.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "switch_profiles")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SwitchProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "profile_name", nullable = false, unique = true)
    private String profileName;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "environment", nullable = false)
    @Builder.Default
    private Environment environment = Environment.DEV;

    @Column(name = "host")
    private String host;

    @Column(name = "port")
    private Integer port;

    @Column(name = "timezone", nullable = false)
    @Builder.Default
    private String timezone = "UTC";

    @Column(name = "connection_timeout_ms", nullable = false)
    @Builder.Default
    private Integer connectionTimeoutMs = 30000;

    @Column(name = "tpdu_enabled", nullable = false)
    @Builder.Default
    private Boolean tpduEnabled = false;

    @Column(name = "tpdu_value", length = 20)
    private String tpduValue;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private Boolean active = false;

    @Column(name = "is_default", nullable = false)
    @Builder.Default
    private Boolean isDefault = false;

    @Column(name = "last_tested_at")
    private LocalDateTime lastTestedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "last_test_result", nullable = false)
    @Builder.Default
    private TestResult lastTestResult = TestResult.UNTESTED;

    @Column(name = "last_test_latency_ms")
    private Integer lastTestLatencyMs;

    @Column(name = "last_test_message", length = 500)
    private String lastTestMessage;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "updated_by")
    private String updatedBy;

    public enum Environment {
        PROD, UAT, DEV
    }

    public enum TestResult {
        OK, FAILED, UNTESTED
    }
}