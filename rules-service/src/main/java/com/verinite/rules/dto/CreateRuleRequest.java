package com.verinite.rules.dto;

import jakarta.validation.constraints.*;
import lombok.*;
import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateRuleRequest {

    // profileId / profileName / mti may be injected by BulkImportRulesRequest
    private Long   profileId;
    private String profileName;

    @Size(min = 4, max = 4, message = "mti must be exactly 4 characters")
    private String mti;

    @NotBlank(message = "deNumber is required")
    private String deNumber;

    @NotBlank(message = "fieldName is required")
    private String fieldName;

    private Boolean isMandatory = false;

    private Integer minLength;
    private Integer maxLength;
    private Integer exactLength;

    @NotBlank(message = "dataType is required")
    @Pattern(regexp = "numeric|alpha|alphanumeric|binary|special",
            message = "dataType must be: numeric | alpha | alphanumeric | binary | special")
    private String dataType;

    private String patternRegex;

    @Pattern(regexp = "CRITICAL|WARNING|INFO",
            message = "severity must be CRITICAL, WARNING, or INFO")
    private String severity = "CRITICAL";

    private Integer priority = 1;

    private Boolean isActive = true;

    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;

    private String description;

    private List<String> allowedValues;
}