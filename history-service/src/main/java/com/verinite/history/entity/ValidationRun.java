package com.verinite.history.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "validation_runs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ValidationRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "run_reference", nullable = false, unique = true, length = 30)
    private String runReference;

    // FIX: rawMessage was missing — schema has raw_message LONGTEXT NOT NULL
    // Without this, every INSERT throws a NOT NULL constraint violation.
    @Column(name = "raw_message", nullable = false, columnDefinition = "LONGTEXT")
    private String rawMessage;

    @Column(name = "profile_id")
    private Long profileId;

    @Column(name = "profile_name_snapshot", length = 100)
    private String profileNameSnapshot;

    @Column(name = "format_id")
    private Long formatId;

    @Column(name = "format_name_snapshot", length = 100)
    private String formatNameSnapshot;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "username_snapshot", length = 50)
    private String usernameSnapshot;

    @Column(name = "user_role_snapshot", length = 20)
    private String userRoleSnapshot;

    @Column(name = "mti", length = 4)
    private String mti;

    @Column(name = "mti_description", length = 100)
    private String mtiDescription;

    @Column(name = "bitmap_primary", length = 16)
    private String bitmapPrimary;

    @Column(name = "bitmap_extended", length = 16)
    private String bitmapExtended;

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    private RunStatus status;

    @Column(name = "total_fields_present", nullable = false)
    @Builder.Default
    private Integer totalFieldsPresent = 0;

    @Column(name = "total_errors", nullable = false)
    @Builder.Default
    private Integer totalErrors = 0;

    @Column(name = "critical_count", nullable = false)
    @Builder.Default
    private Integer criticalCount = 0;

    @Column(name = "warning_count", nullable = false)
    @Builder.Default
    private Integer warningCount = 0;

    @Column(name = "info_count", nullable = false)
    @Builder.Default
    private Integer infoCount = 0;

    @Column(name = "response_code", length = 2)
    private String responseCode;

    @Column(name = "response_label", length = 100)
    private String responseLabel;

    @Column(name = "transaction_amount")
    private Long transactionAmount;

    @Column(name = "currency_code", length = 3)
    private String currencyCode;

    @Column(name = "merchant_name", length = 100)
    private String merchantName;

    @Column(name = "terminal_id", length = 20)
    private String terminalId;

    @Column(name = "pan_masked", length = 25)
    private String panMasked;

    @Column(name = "parse_duration_ms")
    private Integer parseDurationMs;

    @Column(name = "validation_duration_ms")
    private Integer validationDurationMs;

    @Column(name = "ai_duration_ms")
    private Integer aiDurationMs;

    @Column(name = "total_duration_ms")
    private Integer totalDurationMs;

    @Column(name = "ai_enabled", nullable = false)
    @Builder.Default
    private Boolean aiEnabled = false;

    @Column(name = "ai_explanation", columnDefinition = "LONGTEXT")
    private String aiExplanation;

    @Column(name = "ai_model_used", length = 100)
    private String aiModelUsed;

    @Column(name = "is_rerun", nullable = false)
    @Builder.Default
    private Boolean isRerun = false;

    @Column(name = "original_run_reference", length = 30)
    private String originalRunReference;

    @Column(name = "client_ip", length = 45)
    private String clientIp;

    @Column(name = "correlation_id", length = 36)
    private String correlationId;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "run", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ValidationRunField> fields = new ArrayList<>();

    @OneToMany(mappedBy = "run", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ValidationRunError> errors = new ArrayList<>();

    public enum RunStatus {
        VALID, INVALID, WARNED, ERROR, PARSE_ERROR
    }
}