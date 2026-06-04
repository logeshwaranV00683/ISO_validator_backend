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

    private String packager;  // optional — default iso87ascii
}