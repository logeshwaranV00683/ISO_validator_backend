package com.verinite.validation.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Request body sent to ai-service /internal/ai/explain.
 * Mirrors com.verinite.ai.dto.AiExplainRequest — kept as a separate DTO
 * so validation-engine has no compile dependency on ai-service classes.
 */
@Data
@Builder
public class AiExplainRequestDto {

    private String runReference;
    private Long   profileId;
    private String profileName;
    private String mti;

    /** Structured error list — maps from the string errors produced by RulesEngine. */
    private List<AiErrorDto> errors;

    /** Parsed DE fields with PAN already masked. */
    private Map<Integer, String> parsedFields;

    private String correlationId;

    @Data
    @Builder
    public static class AiErrorDto {
        private String deNumber;
        private String fieldName;
        private String severity;    // CRITICAL / WARNING / INFO
        private String errorCode;
        private String errorMessage;
    }
}