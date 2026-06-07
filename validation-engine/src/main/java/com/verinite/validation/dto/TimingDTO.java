package com.verinite.validation.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TimingDTO {
    private long parseDurationMs;
    private long validationDurationMs;
    private long totalDurationMs;
}