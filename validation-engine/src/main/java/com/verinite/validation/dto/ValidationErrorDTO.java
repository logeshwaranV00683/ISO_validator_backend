package com.verinite.validation.dto;

import lombok.Builder;
import lombok.Data;

/**
 * A single structured validation error produced by RulesEngine.
 */
@Data
@Builder
public class ValidationErrorDTO {
    private String  severity;       // CRITICAL / WARNING / INFO
    private String  errorCode;      // ERR-DE2-MANDATORY, ERR-DE3-REGEX, etc.
    private String  deNumber;       // "DE2"
    private Integer deNumberInt;    // 2
    private String  fieldName;      // "Primary Account Number"
    private String  message;        // human-readable description
    private String  aiExplanation;  // populated after AI call (nullable)
}