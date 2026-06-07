package com.verinite.validation.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Mirrors profile-service's ProfileFormatResponse.
 * Returned by GET /internal/profiles/{profileId}/format
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProfileFormatResponseDto {
    private Long   formatId;
    private String xmlContent;
    private String mti;
    private Long   profileId;
}