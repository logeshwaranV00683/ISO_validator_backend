package com.verinite.history_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidationRunDto {

    private String runReference;
    private Long profileId;
    private String profileNameSnapshot;
    private String usernameSnapshot;
    private String mti;
    private String mtiDescription;
    private String status;
    private int totalErrors;
    private int criticalCount;
    private int warningCount;
    private int infoCount;
    private String responseCode;
    private String responseLabel;
    private String panMasked;
    private Integer totalDurationMs;
    private boolean aiEnabled;
    private LocalDateTime createdAt;
}
