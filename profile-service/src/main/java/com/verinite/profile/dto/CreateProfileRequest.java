package com.verinite.profile.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateProfileRequest {

    @NotBlank(message = "Profile name is required")
    private String profileName;

    private String description;
}