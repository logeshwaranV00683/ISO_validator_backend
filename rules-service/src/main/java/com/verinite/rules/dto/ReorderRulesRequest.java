package com.verinite.rules.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.*;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReorderRulesRequest {

    @NotEmpty(message = "priorities list cannot be empty")
    private List<RulePriority> priorities;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RulePriority {
        private Long    ruleId;
        private Integer priority;
    }
}