package com.verinite.ai.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;
import java.util.Map;

@Data @Builder
public class TemplateContext {
    private String mti;
    private String profileName;
    private List<ValidationErrorDto> errors;
    private Map<Integer, String> parsedFields;
}