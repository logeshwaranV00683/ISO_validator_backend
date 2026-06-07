package com.verinite.validation.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Generates atomic, unique run references using Redis INCR.
 *
 * Format: VLD-YYYYMMDD-00001
 * Redis key: run_ref:YYYYMMDD (expires after 2 days)
 * 50 parallel calls → all IDs are unique (Redis INCR is atomic)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RunReferenceService {

    private final StringRedisTemplate redisTemplate;

    private static final String KEY_PREFIX = "run_ref:";

    public String generate() {
        String today    = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String redisKey = KEY_PREFIX + today;

        Long seq = redisTemplate.opsForValue().increment(redisKey);
        if (seq == null) {
            log.warn("Redis INCR returned null for key={} — using fallback seq=1", redisKey);
            seq = 1L;
        }
        if (seq == 1L) {
            // First increment today — set TTL so old keys are cleaned up
            redisTemplate.expire(redisKey, Duration.ofDays(2));
        }

        return String.format("VLD-%s-%05d", today, seq);
    }
}