package com.verinite.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SwitchSuggestionResponse {

    private List<SwitchSuggestion> suggestions;
    private String rawMtiHint;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SwitchSuggestion {
        private Long profileId;
        private String profileName;
        private Double confidence;
        private String reason;
    }
}