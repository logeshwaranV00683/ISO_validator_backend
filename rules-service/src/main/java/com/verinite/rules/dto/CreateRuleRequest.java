package com.verinite.rules.dto;

import com.verinite.common.enums.DataType;
import com.verinite.common.enums.Severity;
import jakarta.validation.constraints.*;
import lombok.*;
import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateRuleRequest {

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

    // FIX: @Pattern and @NotBlank cannot be applied to an enum — use @NotNull
    @NotNull(message = "dataType is required")
    private DataType dataType;

    private String patternRegex;

    // FIX: @Pattern cannot be applied to an enum — validation via @NotNull only
    @NotNull(message = "severity is required")
    private Severity severity;

    private Integer priority = 1;

    private Boolean isActive = true;

    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;

    private String description;

    private List<String> allowedValues;
}