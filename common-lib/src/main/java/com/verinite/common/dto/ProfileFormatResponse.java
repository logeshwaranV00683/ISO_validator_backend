package com.verinite.common.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProfileFormatResponse {

    private Long formatId;
    private String xmlContent;
    private String mti;
    private Long profileId;
}