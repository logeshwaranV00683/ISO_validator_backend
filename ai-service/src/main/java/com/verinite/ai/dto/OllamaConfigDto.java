package com.verinite.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OllamaConfigDto {
    private Long   id;
    private String configKey;
    private String configValue;   // masked if isSensitive=true
    private String configType;
    private String description;
    private Boolean isSensitive;
}