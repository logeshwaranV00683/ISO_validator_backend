package com.verinite.validation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ValidationRequest {

    @NotNull(message = "Profile ID is required")
    private Long profileId;

    @NotBlank(message = "Hex message is required")
    private String hexMessage;

    /** Optional custom packager — defaults to iso87ascii if null. */
    private String packager;

    /**
     * Set to true to request AI explanation for validation errors.
     * If false or Ollama is unreachable, aiExplanation will be null.
     * Default: false (caller must explicitly opt in).
     */
    private boolean enableAi = false;
}