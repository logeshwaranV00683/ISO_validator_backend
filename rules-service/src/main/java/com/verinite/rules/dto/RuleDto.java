package com.verinite.rules.dto;

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
    private String  dataType;
    private String  patternRegex;
    private String  severity;
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