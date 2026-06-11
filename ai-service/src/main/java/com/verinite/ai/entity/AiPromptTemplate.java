package com.verinite.ai.entity;

import com.verinite.common.enums.TemplateScope;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "ai_prompt_templates",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_apt_scope_profile",
                        columnNames = {"scope", "profile_id"}
                )
        }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiPromptTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "template_name", nullable = false, length = 100)
    private String templateName;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope", nullable = false)
    private TemplateScope scope;

    @Column(name = "profile_id")
    private Long profileId;

    @Column(name = "profile_name", length = 100)
    private String profileName;

    @Column(name = "prompt_template", columnDefinition = "LONGTEXT", nullable = false)
    private String promptTemplate;

    @Column(name = "variables_used", length = 500)
    private String variablesUsed;

    @Builder.Default
    @Column(name = "current_version", nullable = false)
    private Integer currentVersion = 1;

    @Builder.Default
    @Column(name = "active", nullable = false)
    private Boolean active = true;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

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
}