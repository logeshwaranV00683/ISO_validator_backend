package com.verinite.profile.event;

import lombok.*;
import java.time.Instant;
import java.util.UUID;

/** Proper event envelope — mirrors the rules-service structure. */
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
    private String sourceService = "profile-service";

    @Builder.Default
    private String timestamp = Instant.now().toString();

    private Payload payload;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Payload {
        private String type;       // FORMAT_UPDATED | FORMAT_ROLLED_BACK
        private Long   profileId;
        private Long   formatId;
        private String mti;        // null for format-level events
    }
}