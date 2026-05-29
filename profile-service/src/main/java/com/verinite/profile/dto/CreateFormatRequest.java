package com.verinite.profile.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateFormatRequest {

    @NotNull(message = "Profile ID is required")
    private Long profileId;

    @NotBlank(message = "Format name is required")
    private String formatName;

    @NotBlank(message = "MTI is required")
    private String mti;

    @NotBlank(message = "XML content is required")
    private String xmlContent;

}