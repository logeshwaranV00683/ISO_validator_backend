package com.verinite.history.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "validation_run_errors")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ValidationRunError {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "run_id", nullable = false)
    @ToString.Exclude
    private ValidationRun run;

    @Column(name = "rule_id")
    private Long ruleId;

    @Column(name = "de_number", nullable = false, length = 10)
    private String deNumber;

    @Column(name = "field_name", length = 150)
    private String fieldName;

    @Column(name = "severity", nullable = false)
    @Enumerated(EnumType.STRING)
    private Severity severity;

    @Column(name = "error_code", nullable = false, length = 50)
    private String errorCode;

    @Column(name = "error_message", nullable = false, length = 500)
    private String errorMessage;

    @Column(name = "rule_snapshot", length = 500)
    private String ruleSnapshot;

    @Column(name = "expected_value", length = 500)
    private String expectedValue;

    @Column(name = "actual_value", length = 500)
    private String actualValue;

    @Column(name = "ai_explanation", columnDefinition = "TEXT")
    private String aiExplanation;

    @Column(name = "ai_fix_suggestion", columnDefinition = "TEXT")
    private String aiFixSuggestion;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public enum Severity {
        CRITICAL, WARNING, INFO
    }
}