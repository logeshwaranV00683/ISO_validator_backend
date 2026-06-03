package com.verinite.rules.dto;

import jakarta.validation.constraints.Pattern;
import lombok.*;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateRuleRequest {

    private String  fieldName;
    private Boolean isMandatory;
    private Integer minLength;
    private Integer maxLength;
    private Integer exactLength;

    @Pattern(regexp = "numeric|alpha|alphanumeric|binary|special")
    private String dataType;

    private String patternRegex;

    @Pattern(regexp = "CRITICAL|WARNING|INFO")
    private String severity;

    private Integer  priority;
    private Boolean  active;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
    private String    description;
}