package com.verinite.validation.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Published by profile-service and rules-service when data changes.
 * Consumed by CacheInvalidationListener in validation-engine.
 *
 * eventType values:
 *   RULE_UPDATED, RULE_DELETED   → evict rulesCache for profileId
 *   FORMAT_UPDATED, FORMAT_ROLLED_BACK → evict packagerCache for id (formatId)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CacheInvalidationEvent {
    private String eventType;
    private Long   id;        // formatId (FORMAT events) or ruleId (RULE events)
    private Long   profileId; // populated for RULE events
    private String mti;       // populated for RULE events
    private String timestamp;
}