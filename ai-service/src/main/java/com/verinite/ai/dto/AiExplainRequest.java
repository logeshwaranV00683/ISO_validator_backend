package com.verinite.ai.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class AiExplainRequest {
    private String runReference;
    private Long profileId;
    private String profileName;
    private String mti;
    private List<ValidationErrorDto> errors;
    private Map<Integer, String> parsedFields;
    private String correlationId;
}