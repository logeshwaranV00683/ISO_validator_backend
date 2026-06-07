package com.verinite.validation.service.impl;

import com.verinite.common.util.PanMaskingUtil;
import com.verinite.validation.client.AIServiceClient;
import com.verinite.validation.dto.AiExplainRequestDto;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ValidationServiceImpl implements ValidationService {

    private final ValidationCacheService cacheService;
    private final AIServiceClient        aiServiceClient;
    private final RabbitTemplate         rabbitTemplate;
    private final StringRedisTemplate    redisTemplate;

    private static final String PACKAGER_PATH = "packager/iso87ascii.xml";

    @Override
    public ValidationResponse validate(
            ValidationRequest request,
            String userId,
            String correlationId) {

        // ── Phase 1: Parse ISO message ─────────────────────────────────────
        Map<Integer, String> parsedFields;
        try {
            parsedFields = com.verinite.validation.util.IsoParserUtil
                    .parse(request.getHexMessage(), PACKAGER_PATH);
        } catch (Exception e) {
            log.error("[{}] ISO parse failed: {}", correlationId, e.getMessage());
            throw new RuntimeException("Invalid ISO8583 message: " + e.getMessage());
        }

        // ── Phase 2: Extract MTI ───────────────────────────────────────────
        String mti = parsedFields.getOrDefault(0, "UNKNOWN");
        log.debug("[{}] Parsed MTI={} profileId={}", correlationId, mti, request.getProfileId());

        // ── Phase 3: Fetch profile format (Caffeine cached) ───────────────
        Map<String, Object> formatData = cacheService.getProfileFormat(request.getProfileId());

        // ── Phase 4: Fetch rules (Caffeine cached, 30s TTL) ───────────────
        List<Map<String, Object>> rawRules = cacheService.getRules(request.getProfileId(), mti);

        // ── Phase 5: Convert to engine format ─────────────────────────────
        List<Map<String, String>> engineRules = rawRules.stream()
                .map(r -> Map.of(
                        "ruleType",  resolveRuleType(r),
                        "deField",   String.valueOf(r.get("deNumber")),
                        "ruleValue", resolveRuleValue(r)
                ))
                .toList();

        // ── Phase 6: Evaluate rules — stateless, no I/O ───────────────────
        List<String> errors = RulesEngine.evaluate(parsedFields, engineRules);
        log.debug("[{}] Rule evaluation — {} error(s)", correlationId, errors.size());

        // ── Phase 7: AI explanation (only if requested AND errors exist) ───
        String aiExplanation = null;
        if (request.isEnableAi() && !errors.isEmpty()) {
            aiExplanation = callAiExplain(
                    request.getProfileId(),
                    mti,
                    errors,
                    PanMaskingUtil.maskFields(parsedFields),
                    correlationId);
        }

        // ── Phase 8: Generate run_reference via Redis INCR ─────────────────
        String runReference = generateRunReference();

        // ── Phase 9: Publish async event (fire-and-forget) ────────────────
        try {
            // FIX: use HashMap instead of Map.of() so we can include rawMessage
            // (Map.of max is 10 entries; also, Map.of does not allow null values)
            Map<String, Object> event = new HashMap<>();
            event.put("runReference",   runReference);
            event.put("rawMessage",     request.getHexMessage()); // FIX: was missing — schema raw_message NOT NULL
            event.put("profileId",      request.getProfileId());
            event.put("mti",            mti);
            event.put("userId",         userId != null ? userId : "anonymous");
            event.put("correlationId",  correlationId != null ? correlationId : "");
            event.put("status",         errors.isEmpty() ? "VALID" : "INVALID");
            event.put("errors",         errors);
            event.put("parsedFields",   PanMaskingUtil.maskFields(parsedFields));
            event.put("aiEnabled",      request.isEnableAi());
            event.put("aiExplanation",  aiExplanation != null ? aiExplanation : "");

            rabbitTemplate.convertAndSend("validation.events", "run.completed", event);
            log.debug("[{}] Published validation event runRef={}", correlationId, runReference);
        } catch (Exception e) {
            // MQ down — log and continue, never fail the validation response
            log.warn("[{}] Failed to publish MQ event: {}", correlationId, e.getMessage());
        }

        // ── Phase 10: Return response immediately ──────────────────────────
        return ValidationResponse.builder()
                .runReference(runReference)
                .status(errors.isEmpty() ? "VALID" : "INVALID")
                .profileId(request.getProfileId())
                .mti(mti)
                .parsedFields(PanMaskingUtil.maskFields(parsedFields))   // PAN never raw
                .errors(errors)
                .aiExplanation(aiExplanation)    // null when AI disabled/skipped/unreachable
                .build();
    }

    // ── AI call — wrapped so it NEVER propagates exceptions ──────────────────

    private String callAiExplain(Long profileId,
                                 String mti,
                                 List<String> errorStrings,
                                 Map<Integer, String> maskedFields,
                                 String correlationId) {
        try {
            List<AiExplainRequestDto.AiErrorDto> errorDtos = mapErrorStrings(errorStrings);

            AiExplainRequestDto req = AiExplainRequestDto.builder()
                    .profileId(profileId)
                    .mti(mti)
                    .errors(errorDtos)
                    .parsedFields(maskedFields)
                    .correlationId(correlationId)
                    .build();

            com.verinite.common.dto.ApiResponse<String> response = aiServiceClient.explain(req);
            return (response != null) ? response.getData() : null;

        } catch (Exception e) {
            log.warn("[AI] explain call failed for mti={}: {}", mti, e.getMessage());
            return null;
        }
    }

    private List<AiExplainRequestDto.AiErrorDto> mapErrorStrings(List<String> errorStrings) {
        return errorStrings.stream()
                .map(err -> {
                    String deNumber = "UNKNOWN";
                    if (err != null && err.startsWith("DE")) {
                        int spaceIdx = err.indexOf(' ');
                        if (spaceIdx > 0) deNumber = err.substring(0, spaceIdx);
                    }
                    return AiExplainRequestDto.AiErrorDto.builder()
                            .deNumber(deNumber)
                            .severity("CRITICAL")
                            .errorMessage(err)
                            .build();
                })
                .collect(Collectors.toList());
    }

    // ── Redis run_reference generator ───────────────────────────────────────

    private String generateRunReference() {
        String today    = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String redisKey = "run_ref:" + today;
        Long   seq      = redisTemplate.opsForValue().increment(redisKey);
        if (seq != null && seq == 1) {
            redisTemplate.expire(redisKey, java.time.Duration.ofDays(2));
        }
        return String.format("VLD-%s-%05d", today, seq != null ? seq : 1);
    }

    // ── Rule type/value resolver helpers ────────────────────────────────────

    private String resolveRuleType(Map<String, Object> rule) {
        if (Boolean.TRUE.equals(rule.get("isMandatory"))) return "MANDATORY";
        if (rule.get("patternRegex")  != null)            return "REGEX";
        if (rule.get("allowedValues") != null)            return "ALLOWED_VALUES";
        if (rule.get("maxLength")     != null)            return "MAX_LENGTH";
        if (rule.get("minLength")     != null)            return "MIN_LENGTH";
        return "MANDATORY";
    }

    private String resolveRuleValue(Map<String, Object> rule) {
        if (rule.get("patternRegex")  != null) return String.valueOf(rule.get("patternRegex"));
        if (rule.get("allowedValues") != null) return String.valueOf(rule.get("allowedValues"));
        if (rule.get("maxLength")     != null) return String.valueOf(rule.get("maxLength"));
        if (rule.get("minLength")     != null) return String.valueOf(rule.get("minLength"));
        return "";
    }
}