package com.verinite.profile.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Mirrors rules-service's BulkImportRulesRequest wire shape. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkImportRulesRequest {
    private Long profileId;
    private String profileName;
    private String mti;
    private String strategy; // "MERGE" | "REPLACE"
    private List<RuleItem> rules;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RuleItem {
        private Long profileId;
        private String profileName;
        private String mti;
        private String deNumber;
        private String fieldName;
        private Boolean isMandatory;
        private Integer maxLength;
        private String dataType;
        private String severity; // CRITICAL|WARNING|INFO
        private Integer priority;
        private Boolean isActive;
        private String description;
    }
}