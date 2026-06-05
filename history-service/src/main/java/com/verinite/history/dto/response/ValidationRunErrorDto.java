package com.verinite.history_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidationRunErrorDto {

    private String deNumber;
    private String fieldName;
    private String severity;
    private String errorCode;
    private String issueDescription;
    private String ruleSnapshot;
    private String expectedValue;
    private String actualValue;
    private String aiExplanation;
    private String aiFixSuggestion;
}