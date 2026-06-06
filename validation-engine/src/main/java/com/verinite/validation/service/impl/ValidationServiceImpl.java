package com.verinite.validation.service.impl;

import com.verinite.common.util.PanMaskingUtil;
import com.verinite.validation.dto.ValidationRequest;
import com.verinite.validation.dto.ValidationResponse;
import com.verinite.validation.engine.RulesEngine;
import com.verinite.validation.service.ValidationCacheService;
import com.verinite.validation.service.ValidationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ValidationServiceImpl implements ValidationService {

    private final ValidationCacheService cacheService;

    private final RabbitTemplate rabbitTemplate;
    private final StringRedisTemplate redisTemplate;

    private static final String PACKAGER_PATH = "packager/iso87ascii.xml";

    @Override
    public ValidationResponse validate(
            ValidationRequest request,
            String userId,
            String correlationId) {

        // Step 1: jPOS parse — hex → Map<DE, value>
        Map<Integer, String> parsedFields;
        try {
            parsedFields = com.verinite.validation.util.IsoParserUtil
                    .parse(request.getHexMessage(), PACKAGER_PATH);
        } catch (Exception e) {
            log.error("[{}] ISO parse failed: {}", correlationId, e.getMessage());
            throw new RuntimeException("Invalid ISO8583 message: " + e.getMessage());
        }

        // Step 2: MTI extract
        String mti = parsedFields.getOrDefault(0, "UNKNOWN");
        log.debug("[{}] Parsed MTI={} profileId={}", correlationId, mti, request.getProfileId());

        // Step 3: Fetch profile format (Caffeine cached — 30s TTL via application.yaml)
        Map<String, Object> formatData = cacheService.getProfileFormat(request.getProfileId());
        String xmlContent = (String) formatData.get("xmlContent");
        log.debug("[{}] Fetched format for profileId={}", correlationId, request.getProfileId());

        // Step 4: Fetch rules (Caffeine cached)
        List<Map<String, Object>> rawRules = cacheService.getRules(request.getProfileId(), mti);

        // Step 5: Convert rules to RulesEngine format (List<Map<String,String>>)
        List<Map<String, String>> engineRules = rawRules.stream()
                .map(r -> Map.of(
                        "ruleType",  resolveRuleType(r),
                        "deField",   String.valueOf(r.get("deNumber")),
                        "ruleValue", resolveRuleValue(r)
                ))
                .toList();

        // Step 6: Evaluate rules — stateless, no I/O
        List<String> errors = RulesEngine.evaluate(parsedFields, engineRules);
        log.debug("[{}] Rule evaluation — {} error(s)", correlationId, errors.size());

        // Step 7: Generate run_reference via Redis INCR
        String runReference = generateRunReference();

        // Step 8: Publish event ASYNC — don't wait
        try {
            Map<String, Object> event = Map.of(
                    "runReference", runReference,
                    "profileId",    request.getProfileId(),
                    "mti",          mti,
                    "userId",       userId != null ? userId : "unknown",
                    "correlationId", correlationId != null ? correlationId : "",
                    "status",       errors.isEmpty() ? "VALID" : "INVALID",
                    "errors",       errors,
                    "parsedFields", PanMaskingUtil.maskFields(parsedFields)
            );
            rabbitTemplate.convertAndSend("validation.events", "run.completed", event);
            log.debug("[{}] Published validation event runRef={}", correlationId, runReference);
        } catch (Exception e) {
            // MQ down — log and continue, don't fail the response
            log.warn("[{}] Failed to publish MQ event: {}", correlationId, e.getMessage());
        }

        // Step 9: Return IMMEDIATELY — PAN masked
        return ValidationResponse.builder()
                .runReference(runReference)
                .status(errors.isEmpty() ? "VALID" : "INVALID")
                .profileId(request.getProfileId())
                .mti(mti)
                .parsedFields(PanMaskingUtil.maskFields(parsedFields))  // ✅ PAN never raw
                .errors(errors)
                .aiExplanation(null)  // AI service Sprint 2-ல் pending
                .build();
    }


    // ── Redis run_reference generator ───────────────────────────────────────

    private String generateRunReference() {
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String redisKey = "run_ref:" + today;
        Long seq = redisTemplate.opsForValue().increment(redisKey);
        // Set TTL on first creation so key auto-expires after 2 days
        if (seq != null && seq == 1) {
            redisTemplate.expire(redisKey,
                    java.time.Duration.ofDays(2));
        }
        return String.format("VLD-%s-%05d", today, seq != null ? seq : 1);
    }

    // ── Helpers — ValidationRule Map → RulesEngine format ───────────────────

    private String resolveRuleType(Map<String, Object> rule) {
        // ValidationRule entity-ல் separate rule type field இல்ல
        // isMandatory, patternRegex, allowedValues, min/maxLength check பண்ண
        if (Boolean.TRUE.equals(rule.get("isMandatory"))) return "MANDATORY";
        if (rule.get("patternRegex") != null)             return "REGEX";
        if (rule.get("allowedValues") != null)            return "ALLOWED_VALUES";
        if (rule.get("maxLength") != null)                return "MAX_LENGTH";
        if (rule.get("minLength") != null)                return "MIN_LENGTH";
        return "MANDATORY"; // fallback
    }

    private String resolveRuleValue(Map<String, Object> rule) {
        if (rule.get("patternRegex") != null)  return String.valueOf(rule.get("patternRegex"));
        if (rule.get("allowedValues") != null) return String.valueOf(rule.get("allowedValues"));
        if (rule.get("maxLength") != null)     return String.valueOf(rule.get("maxLength"));
        if (rule.get("minLength") != null)     return String.valueOf(rule.get("minLength"));
        return "";
    }
}