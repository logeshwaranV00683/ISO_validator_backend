package com.verinite.validation.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Mirrors rules-service's ValidationRule entity JSON.
 * Returned by GET /internal/rules/effective?profileId=&mti=
 *
 * deNumber stored as "DE2" or "2" — both are handled by RulesEngine.parseDeNumber().
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class EffectiveRuleDto {
    private Long    id;
    private Long    profileId;
    private String  profileName;
    private String  mti;
    private String  deNumber;       // e.g. "DE2" or "2"
    private String  fieldName;
    private Boolean isMandatory     = false;
    private Integer minLength;
    private Integer maxLength;
    private Integer exactLength;
    private String  dataType;       // NUMERIC / ALPHA / ALPHANUMERIC / BINARY / SPECIAL
    private String  patternRegex;
    private String  severity        = "CRITICAL"; // CRITICAL / WARNING / INFO
    private Integer priority        = 1;
    private Boolean active          = true;
    private String  description;
    private List<AllowedValueDto> allowedValues = new ArrayList<>();
}