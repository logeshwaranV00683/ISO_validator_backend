package com.verinite.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Minimal mirror of profile-service's FormatDto — only the fields BrdConfirmService needs. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FormatDto {
    private Long id;
    private Long profileId;
    private String formatName;
    private String mti;
}