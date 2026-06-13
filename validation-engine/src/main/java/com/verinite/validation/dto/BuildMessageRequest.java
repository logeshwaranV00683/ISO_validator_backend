package com.verinite.validation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.util.Map;

@Data
public class BuildMessageRequest {

    @NotNull(message = "profileId is required")
    private Long profileId;

    @NotBlank(message = "mti is required")
    private String mti;

    /**
     * Output format for rawMessage in the response.
     * "HEX"   → packed bytes as uppercase hex string (default)
     * "ASCII" → packed bytes as raw ISO-8859-1 string (wire format)
     */
    @Pattern(
            regexp = "^(HEX|ASCII)$",
            message = "outputFormat must be either HEX or ASCII"
    )
    @Schema(description = "Output format: HEX (default) or ASCII")
    private String outputFormat = "HEX";

    /**
     * Map of DE number (integer) → value string.
     * Example: {2: "4111111111111111", 3: "000000", 4: "000000010000"}
     */
    private Map<Integer, String> fields;
}