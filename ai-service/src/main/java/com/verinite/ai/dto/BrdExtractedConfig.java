package com.verinite.ai.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BrdExtractedConfig {

    private BrdSwitchProfileDto switchProfile;
    private String mti;
    private List<BrdFieldDefinitionDto> fieldDefinitions;
    private List<BrdRuleDto> rules;
    private Double confidence;

    @JsonDeserialize(using = LenientStringListDeserializer.class)
    private List<String> warnings;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BrdSwitchProfileDto {
        private String profileName;
        private String description;
        private String environment;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BrdFieldDefinitionDto {
        private String deNumber;
        private String fieldName;
        private String dataType;
        private Integer maxLength;
        private Boolean isMandatory;
        private Boolean isLlvar;
        private Boolean isLllvar;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BrdRuleDto {
        private String deNumber;
        private String fieldName;
        private String dataType;
        private String severity;
        private Boolean isMandatory;
        private Integer minLength;
        private Integer maxLength;
    }
}