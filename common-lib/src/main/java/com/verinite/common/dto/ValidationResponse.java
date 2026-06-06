package com.verinite.common.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class ValidationResponse {

    private String runReference;
    private String status;           // VALID / INVALID
    private Long profileId;
    private String mti;
    private Map<Integer, String> parsedFields;
    private List<String> errors;
    private String aiExplanation;

    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();
}