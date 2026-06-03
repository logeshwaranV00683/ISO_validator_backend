package com.verinite.rules.dto;

import jakarta.validation.constraints.*;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateFieldDefinitionRequest {

    private Long   profileId;
    private String profileName;

    @Size(min = 4, max = 4, message = "mti must be exactly 4 characters")
    private String mti;

    @NotBlank(message = "deNumber is required")
    private String deNumber;

    @NotBlank(message = "fieldName is required")
    private String fieldName;

    @NotBlank(message = "dataType is required")
    @Pattern(regexp = "numeric|alpha|alphanumeric|binary|special",
            message = "dataType must be: numeric | alpha | alphanumeric | binary | special")
    private String dataType;

    @NotNull(message = "maxLength is required")
    private Integer maxLength;

    private Boolean isLlvar          = false;
    private Boolean isLllvar         = false;
    private Boolean isMandatory      = false;
    private String  placeholderValue;
    private Integer displayOrder     = 0;
    private Boolean isBuilderVisible = true;
    private Boolean isActive         = true;
}