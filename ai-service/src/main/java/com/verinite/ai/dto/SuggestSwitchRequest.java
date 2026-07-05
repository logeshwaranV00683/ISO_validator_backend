package com.verinite.ai.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SuggestSwitchRequest {

    @NotBlank(message = "rawMessage is required")
    private String rawMessage;
}