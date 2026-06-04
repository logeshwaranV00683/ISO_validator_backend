package com.verinite.rules.dto;

import com.verinite.common.enums.DataType;
import jakarta.validation.constraints.Pattern;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateFieldDefinitionRequest {

    private String  fieldName;

    @Pattern(regexp = "numeric|alpha|alphanumeric|binary|special")
    private DataType dataType;

    private Integer maxLength;
    private Boolean isLlvar;
    private Boolean isLllvar;
    private Boolean isMandatory;
    private String  placeholderValue;
    private Integer displayOrder;
    private Boolean isBuilderVisible;
    private Boolean isActive;
    private String description;
}