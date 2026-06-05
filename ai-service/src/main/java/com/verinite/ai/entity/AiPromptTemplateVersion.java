package com.verinite.ai.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "ai_prompt_template_versions")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class AiPromptTemplateVersion {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "template_id", nullable = false)
    private Long templateId;

    @Column(name = "version_number", nullable = false)
    private Integer versionNumber;

    @Column(name = "prompt_content", columnDefinition = "LONGTEXT", nullable = false)
    private String promptContent;

    @Column(name = "change_note")
    private String changeNote;

    @Column(name = "is_current", nullable = false)
    @Builder.Default
    private Boolean isCurrent = false;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_by")
    private String createdBy;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
    }
}