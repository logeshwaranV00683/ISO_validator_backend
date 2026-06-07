package com.verinite.ai.entity;

import com.verinite.common.enums.ConfigType;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "ollama_config")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class OllamaConfig {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "config_key", nullable = false, unique = true)
    private String configKey;

    @Column(name = "config_value", columnDefinition = "LONGTEXT", nullable = false)
    private String configValue;

    @Column(name = "config_type", nullable = false)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private ConfigType configType = ConfigType.STRING;

    private String description;

    @Column(name = "is_sensitive", nullable = false)
    @Builder.Default
    private Boolean isSensitive = false;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "updated_by")
    private String updatedBy;

    @PrePersist @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}