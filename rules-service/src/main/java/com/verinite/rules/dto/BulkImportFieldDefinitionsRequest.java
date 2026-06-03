package com.verinite.rules.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BulkImportFieldDefinitionsRequest {

    @NotNull(message = "profileId is required")
    private Long profileId;

    @NotBlank(message = "profileName is required")
    private String profileName;

    @NotBlank(message = "mti is required")
    @Size(min = 4, max = 4)
    private String mti;

    /** MERGE or REPLACE */
    private String strategy = "MERGE";

    @NotEmpty(message = "definitions list cannot be empty")
    private List<CreateFieldDefinitionRequest> definitions;
}