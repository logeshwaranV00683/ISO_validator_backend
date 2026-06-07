package com.verinite.validation.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class BuildMessageResponse {
    private String              hexMessage;
    private String              mti;
    private Long                profileId;
    private List<String>        validationWarnings;  // field-level warnings (not fatal)
}