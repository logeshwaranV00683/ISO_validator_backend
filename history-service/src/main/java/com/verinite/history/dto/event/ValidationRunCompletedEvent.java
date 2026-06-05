package com.verinite.history_service.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidationRunCompletedEvent {

    private String eventId;
    private String eventType;
    private String sourceService;
    private String correlationId;
    private LocalDateTime timestamp;

    // Core run data
    private String runReference;
    private Long profileId;
    private String profileNameSnapshot;
    private Long formatId;
    private String formatNameSnapshot;
    private Long userId;
    private String usernameSnapshot;
    private String userRoleSnapshot;
    private String rawMessage;
    private String mti;
    private String mtiDescription;
    private String bitmapPrimary;
    private String bitmapExtended;
    private String status;
    private int totalFieldsPresent;
    private int totalFieldsParsed;
    private int totalErrors;
    private int criticalCount;
    private int warningCount;
    private int infoCount;
    private String responseCode;
    private String responseLabel;
    private Long transactionAmount;
    private String currencyCode;
    private String merchantName;
    private String terminalId;
    private String panMasked;
    private Integer parseDurationMs;
    private Integer validationDurationMs;
    private Integer aiDurationMs;
    private Integer totalDurationMs;
    private boolean aiEnabled;
    private String aiExplanation;
    private String aiModelUsed;
    private boolean isRerun;
    private String originalRunReference;
    private String clientIp;

    // Nested data
    private List<ParsedFieldEvent> parsedFields;
    private List<ValidationErrorEvent> errors;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ParsedFieldEvent {
        private String deNumber;
        private String fieldName;
        private String rawValue;
        private String displayValue;
        private boolean isPresent;
        private Integer fieldLength;
        private Integer dePosition;
        private String encodingType;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ValidationErrorEvent {
        private String deNumber;
        private String fieldName;
        private String severity;
        private String errorCode;
        private String issueDescription;
        private String ruleSnapshot;
        private String expectedValue;
        private String actualValue;
        private String aiExplanation;
        private String aiFixSuggestion;
    }
}
