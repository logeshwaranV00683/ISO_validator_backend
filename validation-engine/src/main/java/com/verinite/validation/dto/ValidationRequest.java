package com.verinite.validation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ValidationRequest {

    @NotNull(message = "profileId is required")
    private Long profileId;

    /** ISO 8583 hex-encoded message string. */
    @NotBlank(message = "rawMessage is required")
    private String rawMessage;

    /**
     * Set true to request AI explanation for validation errors.
     * Gracefully skipped if AI service is unavailable or no errors exist.
     */
    private boolean enableAi = false;
}