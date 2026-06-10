package com.verinite.profile.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateProfileRequest {

    // --- Identity ---

    @Size(max = 100, message = "Profile name must not exceed 100 characters")
    private String profileName;

    @Size(max = 255, message = "Description must not exceed 255 characters")
    private String description;

    // --- Connection ---

    @Pattern(
            regexp = "^(([a-zA-Z0-9]|[a-zA-Z0-9][a-zA-Z0-9\\-]*[a-zA-Z0-9])\\.)*"
                    + "([A-Za-z0-9]|[A-Za-z0-9][A-Za-z0-9\\-]*[A-Za-z0-9])$",
            message = "Host must be a valid hostname or IP address"
    )
    private String host;

    @Min(value = 1, message = "Port must be between 1 and 65535")
    @Max(value = 65535, message = "Port must be between 1 and 65535")
    private Integer port;

    @Pattern(
            regexp = "^[A-Za-z]+/[A-Za-z_]+$",
            message = "Timezone must be a valid IANA timezone (e.g. Asia/Kolkata)"
    )
    private String timezone;

    @Positive(message = "Connection timeout must be a positive value")
    private Integer connectionTimeoutMs;

    // --- Flags ---

    private Boolean tpduEnabled;

    @Pattern(regexp = "^\\d{10}$", message = "TPDU value must be exactly 10 digits")
    String tpduValue;

    private Boolean isActive;

}