package com.verinite.rules.dto;

import com.verinite.common.enums.DataType;
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

    // FIX: @Pattern and @NotBlank cannot be applied to enum — use @NotNull
    @NotNull(message = "dataType is required")
    private DataType dataType;

    @NotNull(message = "maxLength is required")
    private Integer maxLength;

    private Boolean isLlvar          = false;
    private Boolean isLllvar         = false;
    private Boolean isMandatory      = false;
    private String  placeholderValue;
    private Integer displayOrder     = 0;
    private Boolean isBuilderVisible = true;
    private Boolean isActive         = true;
    private String  description;
}