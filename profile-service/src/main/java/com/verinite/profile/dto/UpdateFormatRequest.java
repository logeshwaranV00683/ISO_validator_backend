package com.verinite.profile.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdateFormatRequest {

    @NotBlank(message = "XML content is required")
    private String xmlContent;
}