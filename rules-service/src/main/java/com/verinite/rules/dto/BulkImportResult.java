package com.verinite.rules.dto;

import lombok.*;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BulkImportResult {
    private int          imported;
    private int          updated;
    private int          skipped;
    private List<String> errors;
}