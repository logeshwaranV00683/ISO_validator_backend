package com.verinite.profile.dto;

import com.verinite.common.enums.Environment;
import com.verinite.profile.entity.SwitchProfile;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ProfileDto {

    // ── Identity ──────────────────────────────────────────────────────
    private Long        id;
    private String      profileName;
    private String      description;
    private Environment environment;

    // ── Connection ────────────────────────────────────────────────────
    private String  host;
    private Integer port;
    private String  timezone;
    private Integer connectionTimeoutMs;

    // ── TPDU ──────────────────────────────────────────────────────────
    private Boolean tpduEnabled;
    private String  tpduValue;

    // ── Flags ─────────────────────────────────────────────────────────
    private Boolean active;
    private Boolean isDefault;

    // ── Usage & test tracking ─────────────────────────────────────────
    private LocalDateTime            lastUsedAt;        // was missing
    private LocalDateTime            lastTestedAt;
    private SwitchProfile.TestResult lastTestResult;
    private Integer                  lastTestLatencyMs;
    private String                   lastTestMessage;

    // ── Audit ─────────────────────────────────────────────────────────
    private String        createdBy;
    private String        updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}