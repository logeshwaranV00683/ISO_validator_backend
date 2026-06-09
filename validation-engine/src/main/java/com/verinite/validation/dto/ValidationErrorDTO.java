package com.verinite.validation.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ValidationErrorDTO {
    private String  severity;           // CRITICAL / WARNING / INFO
    private String  errorCode;          // ERR-DE2-MANDATORY, ERR-DE3-REGEX, etc.
    private String  deNumber;           // "DE2"
    private Integer deNumberInt;        // 2
    private String  fieldName;
    private String  issueDescription;   // renamed from message (F10e)
    private String  ruleSnapshot;       // the rule text that was violated (F10e)
    private String  aiExplanation;
    private String  aiFixSuggestion;    // AI-provided fix hint (F10e)
}