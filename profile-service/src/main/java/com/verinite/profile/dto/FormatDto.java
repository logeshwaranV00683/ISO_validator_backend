package com.verinite.profile.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class FormatDto {

    private Long id;
    private Long profileId;
    private String formatName;
    private String mti;
    private String xmlContent;
    private Integer currentVersion;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}