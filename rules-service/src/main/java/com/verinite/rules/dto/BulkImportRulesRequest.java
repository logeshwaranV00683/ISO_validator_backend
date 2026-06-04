package com.verinite.rules.dto;

import jakarta.validation.constraints.*;
import lombok.*;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BulkImportRulesRequest {

    @NotNull(message = "profileId is required")
    private Long profileId;

    @NotBlank(message = "profileName is required")
    private String profileName;

    @NotBlank(message = "mti is required")
    @Size(min = 4, max = 4, message = "mti must be exactly 4 characters")
    private String mti;

    /**
     * MERGE  = upsert — insert new, update existing (matched by deNumber)
     * REPLACE = soft-delete all existing for profile+mti, then insert all new
     */
    @Pattern(regexp = "MERGE|REPLACE", message = "strategy must be MERGE or REPLACE")
    private String strategy = "MERGE";

    @NotEmpty(message = "rules list cannot be empty")
    private List<CreateRuleRequest> rules;
}