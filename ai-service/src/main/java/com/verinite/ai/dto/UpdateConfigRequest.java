package com.verinite.ai.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdateConfigRequest {

    @NotBlank(message = "Config value must not be blank")
    private String value;

    /** Optional note about why the value was changed (stored in audit log). */
    private String changeNote;
}