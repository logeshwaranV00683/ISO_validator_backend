package com.verinite.rules.dto;

import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RuleDto {

    private Long id;
    private Long profileId;
    private String mti;
    private Integer fieldId;
    private String ruleType;
    private Integer maxLength;
    private Integer minLength;
    private String regexPattern;
    private Boolean active;
    private LocalDateTime createdAt;
    private List<String> allowedValues;
}