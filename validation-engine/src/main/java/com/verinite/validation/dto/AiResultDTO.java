package com.verinite.validation.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AiResultDTO {
    private String  explanation;   // single explanation string from AI service
    private String  modelUsed;
    private long    durationMs;
    private boolean skipped;
    private String  skipReason;    // AI_DISABLED / AI_UNAVAILABLE / NO_ERRORS / null
}