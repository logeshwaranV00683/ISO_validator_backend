package com.verinite.history_service.dto.response;

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
public class ValidationRunDetailDto {

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
    private String correlationId;
    private LocalDateTime createdAt;

    private List<ValidationRunFieldDto> parsedFields;
    private List<ValidationRunErrorDto> errors;
}
