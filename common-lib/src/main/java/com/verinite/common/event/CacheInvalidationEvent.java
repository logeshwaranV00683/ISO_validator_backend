package com.verinite.common.event;

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

    private String sourceService;

    @Builder.Default
    private String timestamp = Instant.now().toString();

    private Payload payload;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Payload {
        private String type;       // RULE_UPDATED | RULE_DELETED | FORMAT_UPDATED | FORMAT_ROLLED_BACK
        private Long   profileId;
        private String mti;
        private Long   formatId;
    }
}