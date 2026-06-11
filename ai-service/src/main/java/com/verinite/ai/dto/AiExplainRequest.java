package com.verinite.ai.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiExplainRequest {

    @NotBlank(message = "Run Reference must not be blank")
    private String runReference;

    private Long profileId;

    private String profileName;

    @NotBlank(message = "MTI must not be blank")
    private String mti;

    private List<ValidationErrorDto> errors;

    private Map<Integer, String> parsedFields;

    private String correlationId;
}