package com.verinite.validation.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * CRITICAL FIX: The previous flat structure read `eventType` from the outer
 * envelope (always "CACHE_INVALIDATION"), never matching the switch cases
 * "RULE_UPDATED", "FORMAT_UPDATED" etc.  The actual type lives in payload.type.
 *
 * Published by profile-service and rules-service as:
 * {
 *   "eventType": "CACHE_INVALIDATION",
 *   "sourceService": "rules-service",
 *   "payload": {
 *     "type":      "RULE_UPDATED",
 *     "profileId": 1,
 *     "mti":       "0200",
 *     "formatId":  null
 *   }
 * }
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CacheInvalidationEvent {

    private String  eventId;
    private String  eventType;     // always "CACHE_INVALIDATION" — NOT the type to switch on
    private String  sourceService;
    private String  timestamp;

    // FIX: the real event type is here
    private Payload payload;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Payload {
        private String type;       // RULE_UPDATED | RULE_DELETED | FORMAT_UPDATED | FORMAT_ROLLED_BACK
        private Long   profileId;
        private String mti;
        private Long   formatId;
    }
}