package com.verinite.ai.entity;

import com.verinite.common.enums.AiRunStatus;
import com.verinite.common.enums.TemplateScope;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "ai_run_logs")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class AiRunLog {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "run_reference", nullable = false)
    private String runReference;

    @Column(name = "template_id")
    private Long templateId;

    @Enumerated(EnumType.STRING)
    @Column(name = "template_scope_used")
    private TemplateScope templateScopeUsed;

    @Column(name = "profile_id")
    private Long profileId;

    @Column(name = "ollama_endpoint")
    private String ollamaEndpoint;

    @Column(name = "model_name")
    private String modelName;

    @Column(name = "prompt_sent", columnDefinition = "LONGTEXT")
    private String promptSent;

    @Column(name = "response_received", columnDefinition = "LONGTEXT")
    private String responseReceived;

    @Column(name = "http_status_code")
    private Integer httpStatusCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AiRunStatus status;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private Integer retryCount = 0;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "correlation_id")
    private String correlationId;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
    }
}