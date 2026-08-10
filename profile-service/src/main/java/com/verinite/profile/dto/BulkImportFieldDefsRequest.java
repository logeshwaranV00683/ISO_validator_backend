package com.verinite.profile.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Mirrors rules-service's BulkImportFieldDefinitionsRequest wire shape. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkImportFieldDefsRequest {
    private Long profileId;
    private String profileName;
    private String mti;
    private String strategy; // "MERGE" | "REPLACE"
    private List<FieldDefItem> definitions;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FieldDefItem {
        private Long profileId;
        private String profileName;
        private String mti;
        private String deNumber;
        private String fieldName;
        private String dataType; // numeric|alpha|alphanumeric|binary|special
        private Integer maxLength;
        private Boolean isLlvar;
        private Boolean isLllvar;
        private Boolean isMandatory;
        private Integer displayOrder;
        private Boolean isBuilderVisible;
        private Boolean isActive;
        private String description;
    }
}