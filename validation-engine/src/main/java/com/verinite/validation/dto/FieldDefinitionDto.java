package com.verinite.validation.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Mirrors rules-service's FieldDefinition entity JSON.
 * Used by MessageBuilderService for POST /api/v1/validate/build.
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class FieldDefinitionDto {
    private Long    id;
    private Long    profileId;
    private String  mti;
    private String  deNumber;       // e.g. "DE2"
    private String  fieldName;
    private String  dataType;       // NUMERIC / ALPHA / ALPHANUMERIC / BINARY / SPECIAL
    private Integer maxLength;
    private Boolean isLlvar   = false;
    private Boolean isLllvar  = false;
    private Boolean isMandatory = false;
    private Boolean isBuilderVisible = true;
    private String  placeholderValue;
    private Integer displayOrder = 0;
}