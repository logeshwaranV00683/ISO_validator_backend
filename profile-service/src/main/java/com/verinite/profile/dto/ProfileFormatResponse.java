package com.verinite.profile.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfileFormatResponse {
    private Long   formatId;
    private String xmlContent;
    private String mti;
    private Long   profileId;
    private String profileName;   // added — needed by validation-engine for ValidationResponse.profile
}