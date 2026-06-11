package com.verinite.profile.dto;

import com.verinite.profile.entity.MessageFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateFormatRequest {

    @NotNull(message = "Profile ID is required")
    private Long profileId;

    @NotBlank(message = "Format name is required")
    @Size(max = 100, message = "Format name must not exceed 100 characters")
    private String formatName;

    @Size(max = 60, message = "ISO version must not exceed 60 characters")
    private String isoVersion;

    private MessageFormat.Encoding encoding;   // defaults to ASCII in service

    @Size(max = 4, message = "MTI must not exceed 4 characters")
    private String mti;

    private Integer totalFields;               // defaults to 128 in service

    private String description;

    @NotBlank(message = "XML content is required")
    private String xmlContent;
}