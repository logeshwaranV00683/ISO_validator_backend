package com.verinite.profile.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Mirrors rules-service's BulkImportResult. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkImportResultDto {
    private int imported;
    private int updated;
    private int skipped;
    private List<String> errors;
}