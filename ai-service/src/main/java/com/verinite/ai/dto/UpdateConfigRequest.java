package com.verinite.ai.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdateConfigRequest {

    /** Used by PUT /ai/config (no-key path) — ignored by PUT /ai/config/{key} */
    @NotBlank(message = "Config Key must not be blank")
    private String key;
    @NotBlank(message = "Config value must not be blank")
    private String value;
    private String changeNote;
}