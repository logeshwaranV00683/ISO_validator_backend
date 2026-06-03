package com.verinite.rules.event;

import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CacheInvalidationEvent {

    @Builder.Default
    private String eventId = UUID.randomUUID().toString();

    @Builder.Default
    private String eventType = "CACHE_INVALIDATION";

    @Builder.Default
    private String sourceService = "rules-service";

    @Builder.Default
    private String timestamp = Instant.now().toString();

    private Payload payload;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Payload {
        private String type;       // RULE_UPDATED | RULE_DELETED
        private Long   profileId;
        private String mti;
        private Long   formatId;   // null for rule events
    }
}