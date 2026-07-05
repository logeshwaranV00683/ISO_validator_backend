package com.verinite.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Mirrors profile-service's CreateFormatRequest wire shape.
 * encoding must be one of "ASCII" | "EBCDIC" | "Binary".
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateFormatRequest {
    private Long profileId;
    private String formatName;
    private String isoVersion;
    private String encoding;
    private String mti;
    private Integer totalFields;
    private String description;
    private String xmlContent;
}