package com.verinite.profile.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class FormatEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public void publishFormatUpdated(Long profileId, Long formatId) {
        Map<String, Object> event = new HashMap<>();
        event.put("eventType", "FORMAT_UPDATED");
        event.put("profileId", profileId);
        event.put("formatId", formatId);
        event.put("timestamp", LocalDateTime.now().toString());

        rabbitTemplate.convertAndSend("cache.invalidation", "", event);
        log.info("Published FORMAT_UPDATED for profileId={} formatId={}", profileId, formatId);
    }

    public void publishFormatRolledBack(Long profileId, Long formatId) {
        Map<String, Object> event = new HashMap<>();
        event.put("eventType", "FORMAT_ROLLED_BACK");
        event.put("profileId", profileId);
        event.put("formatId", formatId);
        event.put("timestamp", LocalDateTime.now().toString());

        rabbitTemplate.convertAndSend("cache.invalidation", "", event);
        log.info("Published FORMAT_ROLLED_BACK for profileId={} formatId={}", profileId, formatId);
    }
}