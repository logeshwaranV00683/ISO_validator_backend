package com.verinite.ai.entity;

import com.verinite.common.enums.ConfigType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "ollama_config",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_ollama_config_key",
                        columnNames = "config_key"
                )
        }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OllamaConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "config_key", nullable = false, length = 100)
    private String configKey;

    @Column(name = "config_value", columnDefinition = "LONGTEXT", nullable = false)
    private String configValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "config_type", nullable = false)
    @Builder.Default
    private ConfigType configType = ConfigType.STRING;

    @Column(name = "description", length = 255)
    private String description;

    @Builder.Default
    @Column(name = "is_sensitive", nullable = false)
    private Boolean isSensitive = false;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "updated_by", length = 50)
    private String updatedBy;

    @PrePersist
    @PreUpdate
    public void updateTimestamp() {
        updatedAt = LocalDateTime.now();
    }
}