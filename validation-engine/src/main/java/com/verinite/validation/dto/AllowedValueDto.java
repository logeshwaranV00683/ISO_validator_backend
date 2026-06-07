package com.verinite.validation.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Mirrors rules-service's RuleAllowedValue entity JSON.
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AllowedValueDto {
    private Long   id;
    private String allowedValue;
    private String valueLabel;
    private Integer sortOrder;
}