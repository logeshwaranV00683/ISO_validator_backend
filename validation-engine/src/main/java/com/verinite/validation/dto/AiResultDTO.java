package com.verinite.validation.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AiResultDTO {
    private boolean enabled;       // true if AI was requested, even if skipped (F10c)
    private String  explanation;
    private String  modelUsed;
    private long    durationMs;
    private boolean skipped;
    private String  skipReason;    // AI_DISABLED / AI_UNAVAILABLE / NO_ERRORS / PARSE_ERROR
}