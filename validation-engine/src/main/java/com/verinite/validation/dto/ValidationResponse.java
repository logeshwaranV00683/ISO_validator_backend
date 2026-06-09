package com.verinite.validation.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class ValidationResponse {

    private String runReference;
    private String status;              // PASSED | FAILED | WARNED | PARSE_ERROR
    private Long   profileId;
    private String profile;            // profile name string (F10f)
    private String mti;
    private String mtiDescription;     // e.g. "0200 – Financial Transaction Request" (F10f)
    private String message;            // populated on PARSE_ERROR

    private List<ParsedFieldDTO>     parsedFields;
    private List<ValidationErrorDTO> errors;
    private Summary                  summary;   // error counts (F10f)

    private TimingDTO   timing;
    private BitmapDTO   bitmap;
    private AiResultDTO ai;

    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    @Data
    @Builder
    public static class Summary {
        private int criticalCount;
        private int warningCount;
        private int infoCount;
    }
}