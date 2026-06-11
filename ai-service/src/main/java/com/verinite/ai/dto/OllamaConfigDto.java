package com.verinite.ai.dto;

import com.verinite.common.enums.ConfigType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OllamaConfigDto {

    private Long id;

    // Renamed from configKey
    private String key;

    // Renamed from configValue (masked if sensitive)
    private String value;

    @Builder.Default
    private ConfigType configType = ConfigType.STRING;

    private String description;

    private Boolean isSensitive;

    private String updatedBy;

    private LocalDateTime updatedAt;
}