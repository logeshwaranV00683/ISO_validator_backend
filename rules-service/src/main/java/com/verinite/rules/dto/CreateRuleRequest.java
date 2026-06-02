package com.verinite.rules.dto;

import jakarta.validation.constraints.*;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateRuleRequest {

    @NotNull(message = "profileId is required")
    private Long profileId;

    @NotBlank(message = "mti is required")
    @Size(min = 4, max = 4, message = "mti must be exactly 4 characters")
    private String mti;

    @NotNull(message = "fieldId is required")
    private Integer fieldId;

    @NotBlank(message = "ruleType is required")
    private String ruleType;

    private Integer maxLength;
    private Integer minLength;
    private String regexPattern;
}