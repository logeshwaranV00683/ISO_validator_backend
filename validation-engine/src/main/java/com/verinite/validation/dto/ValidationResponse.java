package com.verinite.validation.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class ValidationResponse {

    private String runReference;  // VLD-YYYYMMDD-00001
    /** PASSED | FAILED | WARNED | PARSE_ERROR */
    private String status;
    private Long   profileId;
    private String mti;
    /** Human-readable message — populated on PARSE_ERROR */
    private String message;

    private List<ParsedFieldDTO>   parsedFields;
    private List<ValidationErrorDTO> errors;

    private TimingDTO  timing;
    private BitmapDTO  bitmap;
    private AiResultDTO ai;

    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();
}