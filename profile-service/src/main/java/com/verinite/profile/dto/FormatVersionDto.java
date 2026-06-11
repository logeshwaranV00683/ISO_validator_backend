package com.verinite.profile.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Returned by GET /formats/{id}/versions.
 * Carries full version metadata; xmlContent is intentionally omitted
 * to keep list responses lightweight — fetch full XML via the parent
 * format endpoint or by inspecting a specific rollback target.
 */
@Data
@Builder
public class FormatVersionDto {

    private Long          id;
    private Long          formatId;
    private Integer       versionNumber;
    private String        checksum;
    private String        changeNote;
    private Boolean       isCurrent;
    private Boolean       validatedOk;
    private LocalDateTime validatedAt;
    private LocalDateTime createdAt;
    private String        createdBy;
}