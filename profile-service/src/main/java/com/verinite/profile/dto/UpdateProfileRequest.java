package com.verinite.profile.dto;

import lombok.Data;

@Data
public class UpdateProfileRequest {

    private String profileName;
    private String description;
}