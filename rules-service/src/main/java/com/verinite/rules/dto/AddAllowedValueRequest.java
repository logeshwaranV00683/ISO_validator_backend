package com.verinite.rules.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AddAllowedValueRequest {

    @NotBlank
    private String value;
}