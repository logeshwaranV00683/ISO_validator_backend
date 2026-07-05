package com.verinite.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Mirrors profile-service's CreateProfileRequest wire shape.
 * environment must be one of "DEV" | "UAT" | "PROD" (profile-service's
 * com.verinite.common.enums.Environment values) — kept as String here so
 * ai-service does not import another service's package directly.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateProfileRequest {
    private String profileName;
    private String description;
    private String environment;
    private String host;
    private Integer port;
    private String timezone;
    private Integer connectionTimeoutMs;
    private boolean tpduEnabled;
    private String tpduValue;
    private boolean isActive;
    private boolean isDefault;
}