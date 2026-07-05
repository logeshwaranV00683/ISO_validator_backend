package com.verinite.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Minimal mirror of profile-service's ProfileDto — only the fields BrdConfirmService needs. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfileDto {
    private Long id;
    private String profileName;
    private String description;
    private String environment;
}