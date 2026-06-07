package com.verinite.validation.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class AiExplainRequestDto {

    private String runReference;
    private Long   profileId;
    private String profileName;
    private String mti;

    /** Structured error list — maps from the string errors produced by RulesEngine. */
    private List<AiErrorDto> errors;
    /** Parsed DE fields with PAN already masked — never send raw PAN to AI. */
    private Map<Integer, String> parsedFields;

    private String correlationId;

    @Data
    @Builder
    public static class AiErrorDto {
        private String deNumber;
        private String fieldName;
        private String severity;
        private String errorCode;
        private String errorMessage;
    }
}