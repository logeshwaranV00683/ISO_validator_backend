package com.verinite.profile.dto;

import com.verinite.common.enums.Environment;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class CreateProfileRequest {

    // --- Identity ---

    @NotBlank(message = "Profile name is required")
    @Size(max = 100, message = "Profile name must not exceed 100 characters")
    String profileName;

    @Size(max = 255, message = "Description must not exceed 255 characters")
    String description;

    @NotNull(message = "Environment is required")
    Environment environment;

    @NotNull(message = "formatId is required")
    Long formatId;

    // --- Connection ---

    @Pattern(
            regexp = "^(([a-zA-Z0-9]|[a-zA-Z0-9][a-zA-Z0-9\\-]*[a-zA-Z0-9])\\.)*"
                    + "([A-Za-z0-9]|[A-Za-z0-9][A-Za-z0-9\\-]*[A-Za-z0-9])$",
            message = "Host must be a valid hostname or IP address"
    )
    String host;

    @Min(value = 1, message = "Port must be between 1 and 65535")
    @Max(value = 65535, message = "Port must be between 1 and 65535")
    Integer port;

    @Pattern(
            regexp = "^[A-Za-z]+/[A-Za-z_]+$",
            message = "Timezone must be a valid IANA timezone (e.g. Asia/Kolkata)"
    )
    String timezone;

    @Positive(message = "Connection timeout must be a positive value")
    Integer connectionTimeoutMs;

    // --- Flags ---

    boolean tpduEnabled;

    @Pattern(regexp = "^\\d{10}$", message = "TPDU value must be exactly 10 digits")
    String tpduValue;

    boolean isActive;

    boolean isDefault;

}