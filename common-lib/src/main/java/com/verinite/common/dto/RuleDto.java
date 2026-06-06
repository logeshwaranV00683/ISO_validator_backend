package com.verinite.common.dto;

import com.verinite.common.enums.DataType;
import com.verinite.common.enums.Severity;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RuleDto {

    private Long   id;
    private Long   profileId;
    private String profileName;
    private String mti;
    private String deNumber;
    private String fieldName;
    private Boolean isMandatory;
    private Integer minLength;
    private Integer maxLength;
    private Integer exactLength;
    private DataType dataType;
    private String  patternRegex;
    private Severity severity;
    private Integer priority;
    private Boolean active;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
    private String    description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdByName;
    private String updatedByName;
    private List<String> allowedValues;
}