package com.verinite.history.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatsResponse {

    private long totalRuns;
    private long passed;
    private long failed;
    private long warned;
    private long parseErrors;
    private double passRate;

    private Double avgTotalMs;
    private Double avgAiMs;
    private Long p95TotalMs;       // FIX Bug 5: was missing — StatsService calls .p95TotalMs()

    private long aiSkipCount;
    private long aiErrorCount;

    private Map<String, Long> runsByMti;
    private Map<String, Long> runsByStatus;
    private Map<String, Long> runsByProfile;

    private List<TopErrorField> topErrorFields;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TopErrorField {
        private String deNumber;
        private String fieldName;
        private long errorCount;
    }
}