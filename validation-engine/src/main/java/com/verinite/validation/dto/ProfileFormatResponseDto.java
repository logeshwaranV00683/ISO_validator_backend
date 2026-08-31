package com.verinite.validation.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProfileFormatResponseDto {
    private Long   formatId;
    private String xmlContent;
    private String mti;
    private Long   profileId;
    private String profileName;
    private String environment;
}