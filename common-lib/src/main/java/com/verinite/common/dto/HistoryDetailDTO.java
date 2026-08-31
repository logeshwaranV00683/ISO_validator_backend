package com.verinite.common.dto;

import com.verinite.common.enums.EncodingType;import com.verinite.common.enums.Severity;
import com.verinite.common.enums.RunStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HistoryDetailDTO {
    private Long runId;
    private String runReference;
    private Long profileId;
    private String profileNameSnapshot;
    private String environment;
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
    private RunStatus status;
    private Integer totalFieldsPresent;
    private Integer totalFieldsParsed;
    private Integer totalErrors;
    private Integer criticalCount;
    private Integer warningCount;
    private Integer infoCount;
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
    private Boolean aiEnabled;
    private String aiExplanation;
    private String aiModelUsed;
    private Boolean isRerun;
    private String originalRunReference;
    private String clientIp;
    private String correlationId;
    private LocalDateTime createdAt;
    private List<FieldDto> parsedFields;
    private List<ErrorDto> errors;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FieldDto {
        private String deNumber;
        private String fieldName;
        private String rawValue;
        private String displayValue;
        private Boolean isPresent;
        private Integer fieldLength;
        private Integer dePosition;
        private EncodingType encodingType;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ErrorDto {
        private Long ruleId;
        private String deNumber;
        private String fieldName;
        private Severity severity;
        private String errorCode;
        private String issueDescription;
        private String ruleSnapshot;
        private String expectedValue;
        private String actualValue;
        private String aiExplanation;
        private String aiFixSuggestion;
    }
}