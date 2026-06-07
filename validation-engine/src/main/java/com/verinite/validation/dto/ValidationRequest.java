package com.verinite.validation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ValidationRequest {

    @NotNull(message = "profileId is required")
    private Long profileId;

    @NotBlank(message = "rawMessage is required")
    private String rawMessage;

    @Builder.Default
    private boolean enableAi = false;

    /** Set to true when this request is a rerun of a previous validation. */
    @Builder.Default
    private boolean isRerun = false;

    /** Original run reference when isRerun=true. */
    private String originalRunReference;
}