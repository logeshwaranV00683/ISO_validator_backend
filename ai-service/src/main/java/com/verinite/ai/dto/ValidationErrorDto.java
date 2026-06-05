package com.verinite.ai.dto;

import lombok.Data;

@Data
public class ValidationErrorDto {
    private String deNumber;
    private String fieldName;
    private String severity;
    private String errorCode;
    private String errorMessage;
    private String expectedValue;
    private String actualValue;
}