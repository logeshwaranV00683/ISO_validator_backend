package com.verinite.rules.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateRuleRequest {

    private String ruleType;
    private Integer maxLength;
    private Integer minLength;
    private String regexPattern;
    private Boolean active;
}