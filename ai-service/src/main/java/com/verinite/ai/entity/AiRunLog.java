package com.verinite.ai.entity;

import com.verinite.common.enums.AiRunStatus;
import com.verinite.common.enums.TemplateScope;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "ai_run_logs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiRunLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "run_reference", nullable = false, length = 30)
    private String runReference;

    @Column(name = "template_id")
    private Long templateId;

    @Enumerated(EnumType.STRING)
    @Column(name = "template_scope_used")
    private TemplateScope templateScopeUsed;

    @Column(name = "profile_id")
    private Long profileId;

    @Column(name = "ollama_endpoint", length = 500)
    private String ollamaEndpoint;

    @Column(name = "model_name", length = 100)
    private String modelName;

    @Column(name = "prompt_sent", columnDefinition = "LONGTEXT")
    private String promptSent;

    @Column(name = "response_received", columnDefinition = "LONGTEXT")
    private String responseReceived;

    @Column(name = "http_status_code")
    private Integer httpStatusCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private AiRunStatus status;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Builder.Default
    @Column(name = "retry_count", nullable = false)
    private Integer retryCount = 0;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "correlation_id", length = 36)
    private String correlationId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
    }
}