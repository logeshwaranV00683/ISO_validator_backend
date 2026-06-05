package com.verinite.history.dto.response;

import com.verinite.history.entity.ValidationRun.RunStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HistorySummaryDTO {
    private Long runId;
    private String runReference;
    private Long profileId;
    private String profileNameSnapshot;
    private Long formatId;
    private String formatNameSnapshot;
    private Long userId;
    private String usernameSnapshot;
    private String userRoleSnapshot;
    private String mti;
    private String mtiDescription;
    private RunStatus status;
    private Integer totalFieldsPresent;
    private Integer totalErrors;
    private Integer criticalCount;
    private Integer warningCount;
    private Integer infoCount;
    private String responseCode;
    private String responseLabel;
    private String panMasked;
    private Long transactionAmount;
    private String currencyCode;
    private Integer totalDurationMs;
    private Boolean aiEnabled;
    private LocalDateTime createdAt;
}