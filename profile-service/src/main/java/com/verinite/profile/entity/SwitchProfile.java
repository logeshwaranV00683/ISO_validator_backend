package com.verinite.profile.entity;

import com.verinite.common.enums.Environment;
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

    // --- Identity ---

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "profile_name", nullable = false, unique = true, length = 100)
    private String profileName;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "environment", nullable = false, columnDefinition = "ENUM('PROD','UAT','DEV')")
    @Builder.Default
    private Environment environment = Environment.DEV;

    // --- Connection ---

    @Column(name = "host", nullable = false, length = 255)
    private String host;

    @Column(name = "port", nullable = false)
    private Integer port;

    @Column(name = "timezone", nullable = false, length = 100)
    @Builder.Default
    private String timezone = "UTC";

    @Column(name = "connection_timeout_ms", nullable = false)
    @Builder.Default
    private Integer connectionTimeoutMs = 30000;

    // --- TPDU ---

    @Column(name = "tpdu_enabled", nullable = false)
    @Builder.Default
    private Boolean tpduEnabled = false;

    @Column(name = "tpdu_value", length = 20)
    private String tpduValue;

    // --- Status flags ---

    @Column(name = "active", nullable = false)
    @Builder.Default
    private Boolean active = false;

    @Column(name = "is_default", nullable = false)
    @Builder.Default
    private Boolean isDefault = false;

    // --- Soft delete ---

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    // --- Test tracking ---

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    @Column(name = "last_tested_at")
    private LocalDateTime lastTestedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "last_test_result", nullable = false,
            columnDefinition = "ENUM('OK','FAILED','UNTESTED')")
    @Builder.Default
    private TestResult lastTestResult = TestResult.UNTESTED;

    @Column(name = "last_test_message", length = 500)
    private String lastTestMessage;

    @Column(name = "last_test_latency_ms")
    private Integer lastTestLatencyMs;

    // --- Audit ---

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "created_by", length = 50)
    private String createdBy;

    @Column(name = "updated_by", length = 50)
    private String updatedBy;

    // --- Enum ---

    public enum TestResult {
        OK, FAILED, UNTESTED
    }
}