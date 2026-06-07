package com.verinite.validation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

@Data
public class BuildMessageRequest {

    @NotNull(message = "profileId is required")
    private Long profileId;

    @NotBlank(message = "mti is required")
    private String mti;

    /**
     * Map of DE number (integer) → value string.
     * Example: {2: "4111111111111111", 3: "000000", 4: "000000010000"}
     */
    private Map<Integer, String> fields;
}