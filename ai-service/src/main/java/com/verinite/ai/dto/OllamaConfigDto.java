package com.verinite.ai.dto;

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
    private Long          id;
    private String        key;          // renamed from configKey (F9)
    private String        value;        // renamed from configValue, masked if sensitive (F9)
    private String        configType;
    private String        description;
    private Boolean       isSensitive;
    private String        updatedBy;    // added (F9)
    private LocalDateTime updatedAt;    // added (F9)
}