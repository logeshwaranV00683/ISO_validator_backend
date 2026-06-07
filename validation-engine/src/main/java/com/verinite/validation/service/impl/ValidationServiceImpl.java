package com.verinite.validation.service.impl;

import com.verinite.common.util.PanMaskingUtil;
import com.verinite.validation.client.AIServiceClient;
import com.verinite.validation.client.HistoryServiceClient;
import com.verinite.validation.client.RulesClient;
import com.verinite.validation.dto.*;
import com.verinite.validation.engine.RulesEngine;
import com.verinite.validation.iso.PackagerCache;
import com.verinite.validation.iso.ParsedMessage;
import com.verinite.validation.messaging.ValidationRunPublisher;
import com.verinite.validation.service.RunReferenceService;
import com.verinite.validation.service.ValidationCacheService;
import com.verinite.validation.service.ValidationService;
import com.verinite.validation.util.IsoParserUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.ISOPackager;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ValidationServiceImpl implements ValidationService {

    private final ValidationCacheService cacheService;
    private final PackagerCache          packagerCache;
    private final AIServiceClient        aiServiceClient;
    private final ValidationRunPublisher publisher;
    private final RunReferenceService    runReferenceService;
    private final RulesClient           rulesClient;
    private final HistoryServiceClient historyClient;

    // ─────────────────────────────────────────────────────────────────────────
    //  POST /api/v1/validate  — full 8-phase pipeline
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public ValidationResponse validate(ValidationRequest request,
                                       String userId,
                                       String correlationId) {

        long totalStart = System.currentTimeMillis();
        log.debug("[{}] validate: profileId={} enableAi={}", correlationId,
                request.getProfileId(), request.isEnableAi());

        // ── Phase 1: Fetch profile + format (Feign, Caffeine 30s cache) ──────
        ProfileFormatResponseDto profileFormat;
        try {
            profileFormat = cacheService.getProfileFormat(request.getProfileId());
        } catch (Exception e) {
            log.error("[{}] Profile service unavailable: {}", correlationId, e.getMessage());
            throw new RuntimeException("PROFILE_SERVICE_UNAVAILABLE: " + e.getMessage());
        }

        if (profileFormat == null || profileFormat.getXmlContent() == null) {
            throw new RuntimeException(
                    "PROFILE_NOT_FOUND: No active format for profileId=" + request.getProfileId());
        }

        // ── Phase 2: Load / cache packager (PackagerCache, no TTL) ───────────
        ISOPackager packager;
        try {
            packager = packagerCache.get(profileFormat.getFormatId(), profileFormat.getXmlContent());
        } catch (Exception e) {
            log.error("[{}] PackagerCache load failed formatId={}: {}",
                    correlationId, profileFormat.getFormatId(), e.getMessage());
            throw new RuntimeException("PACKAGER_LOAD_FAILED: " + e.getMessage());
        }

        // ── Phase 3: Parse with jPOS ──────────────────────────────────────────
        long parseStart = System.currentTimeMillis();
        ParsedMessage parsed;
        try {
            parsed = IsoParserUtil.parse(request.getRawMessage(), packager);
        } catch (Exception e) {
            long parseDurationMs = System.currentTimeMillis() - parseStart;
            log.error("[{}] PARSE_ERROR: {}", correlationId, e.getMessage());

            // Generate runRef and publish PARSE_ERROR event — then return 200
            String runRef = runReferenceService.generate();
            TimingDTO timing = TimingDTO.builder()
                    .parseDurationMs(parseDurationMs)
                    .validationDurationMs(0L)
                    .totalDurationMs(System.currentTimeMillis() - totalStart)
                    .build();

            ValidationRunEvent errorEvent = ValidationRunEvent.builder()
                    .runReference(runRef)
                    .status("PARSE_ERROR")
                    .profileId(request.getProfileId())
                    .mti("UNKNOWN")
                    .rawMessage(request.getRawMessage())
                    .userId(userId != null ? userId : "anonymous")
                    .correlationId(correlationId != null ? correlationId : "")
                    .parsedFields(Collections.emptyList())
                    .errors(Collections.emptyList())
                    .timing(timing)
                    .timestamp(LocalDateTime.now())
                    .build();
            publisher.publish(errorEvent);

            return ValidationResponse.builder()
                    .runReference(runRef)
                    .status("PARSE_ERROR")
                    .profileId(request.getProfileId())
                    .mti("UNKNOWN")
                    .message("jPOS: " + e.getMessage())
                    .parsedFields(Collections.emptyList())
                    .errors(Collections.emptyList())
                    .timing(timing)
                    .ai(AiResultDTO.builder().skipped(true).skipReason("PARSE_ERROR").build())
                    .timestamp(LocalDateTime.now())
                    .build();
        }

        long parseDurationMs = System.currentTimeMillis() - parseStart;
        String mti            = parsed.getMti();
        Map<Integer, String> rawFields    = parsed.getFields();
        Map<Integer, String> maskedFields = PanMaskingUtil.maskFields(rawFields);

        log.debug("[{}] Parsed MTI={} fieldCount={}", correlationId, mti, rawFields.size());

        // Compute bitmap from parsedFields
        BitmapDTO bitmap = computeBitmap(rawFields);

        // ── Phase 4: Fetch rules (Feign, Caffeine 30s cache) ─────────────────
        List<EffectiveRuleDto> rules;
        try {
            rules = cacheService.getRules(request.getProfileId(), mti);
        } catch (Exception e) {
            log.warn("[{}] Rules service unavailable — proceeding with empty rules: {}",
                    correlationId, e.getMessage());
            rules = Collections.emptyList();
        }

        // Build DE-number → fieldName map for response construction
        Map<Integer, String> deFieldNames = new HashMap<>();
        for (EffectiveRuleDto rule : rules) {
            int de = RulesEngine.parseDeNumber(rule.getDeNumber());
            if (de > 0 && rule.getFieldName() != null) {
                deFieldNames.put(de, rule.getFieldName());
            }
        }

        // ── Phase 5: Rules Engine (pure in-memory) ────────────────────────────
        long validateStart = System.currentTimeMillis();
        List<ValidationErrorDTO> errors = RulesEngine.evaluate(rawFields, rules);
        long validationDurationMs = System.currentTimeMillis() - validateStart;

        log.debug("[{}] RulesEngine: {} error(s)", correlationId, errors.size());

        // Determine status
        String status = determineStatus(errors);

        // ── Phase 6: AI Explanation ───────────────────────────────────────────
        AiResultDTO aiResult;
        if (!request.isEnableAi()) {
            aiResult = AiResultDTO.builder().skipped(true).skipReason("AI_DISABLED").build();
        } else if (errors.isEmpty()) {
            aiResult = AiResultDTO.builder().skipped(true).skipReason("NO_ERRORS").build();
        } else {
            aiResult = callAiExplain(null /* runRef not yet generated */,
                    request.getProfileId(), mti, errors, maskedFields, correlationId);
        }

        // ── Phase 7: Generate run reference + publish async ──────────────────
        String runRef = runReferenceService.generate();

        long totalDurationMs = System.currentTimeMillis() - totalStart;
        TimingDTO timing = TimingDTO.builder()
                .parseDurationMs(parseDurationMs)
                .validationDurationMs(validationDurationMs)
                .totalDurationMs(totalDurationMs)
                .build();

        List<ParsedFieldDTO> parsedFieldDTOs =
                buildParsedFieldDTOs(maskedFields, rawFields, deFieldNames);

        ValidationRunEvent event = ValidationRunEvent.builder()
                .runReference(runRef)
                .status(status)
                .profileId(request.getProfileId())
                .mti(mti)
                .rawMessage(request.getRawMessage())  // raw — never log, only stored
                .userId(userId != null ? userId : "anonymous")
                .correlationId(correlationId != null ? correlationId : "")
                .parsedFields(parsedFieldDTOs)
                .errors(errors)
                .timing(timing)
                .aiResult(aiResult)
                .timestamp(LocalDateTime.now())
                .build();

        publisher.publish(event); // fire-and-forget

        // ── Phase 8: Return response immediately ──────────────────────────────
        return ValidationResponse.builder()
                .runReference(runRef)
                .status(status)
                .profileId(request.getProfileId())
                .mti(mti)
                .parsedFields(parsedFieldDTOs)
                .errors(errors)
                .timing(timing)
                .bitmap(bitmap)
                .ai(aiResult)
                .timestamp(LocalDateTime.now())
                .build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  POST /api/v1/validate/build
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public BuildMessageResponse buildMessage(BuildMessageRequest request, String userId) {

        // Fetch profile format to get packager
        ProfileFormatResponseDto profileFormat = cacheService.getProfileFormat(request.getProfileId());
        if (profileFormat == null || profileFormat.getXmlContent() == null) {
            throw new RuntimeException(
                    "PROFILE_NOT_FOUND: No active format for profileId=" + request.getProfileId());
        }

        ISOPackager packager = packagerCache.get(profileFormat.getFormatId(),
                profileFormat.getXmlContent());

        // Fetch field definitions and filter builder-visible ones
        List<FieldDefinitionDto> fieldDefs;
        try {
            fieldDefs = rulesClient.getFieldDefinitions(request.getProfileId(), request.getMti());
        } catch (Exception e) {
            throw new RuntimeException("FIELD_DEFINITIONS_UNAVAILABLE: " + e.getMessage());
        }

        Map<String, FieldDefinitionDto> defByDeNum = new HashMap<>();
        for (FieldDefinitionDto fd : fieldDefs) {
            if (Boolean.TRUE.equals(fd.getIsBuilderVisible())) {
                defByDeNum.put(normaliseDeNumber(fd.getDeNumber()), fd);
            }
        }

        List<String> warnings = new ArrayList<>();
        Map<Integer, String> validatedFields = new HashMap<>();

        if (request.getFields() != null) {
            for (Map.Entry<Integer, String> entry : request.getFields().entrySet()) {
                int    de    = entry.getKey();
                String value = entry.getValue();
                String deKey = String.valueOf(de);
                FieldDefinitionDto fd = defByDeNum.get(deKey);

                if (fd != null && fd.getMaxLength() != null
                        && value != null && value.length() > fd.getMaxLength()) {
                    warnings.add("DE" + de + " value length " + value.length()
                            + " exceeds maxLength " + fd.getMaxLength()
                            + " — truncated");
                    value = value.substring(0, fd.getMaxLength());
                }
                validatedFields.put(de, value);
            }
        }

        try {
            ISOMsg msg = IsoParserUtil.buildMsg(request.getMti(), validatedFields, packager);
            byte[] packed = msg.pack();
            String hex    = bytesToHex(packed);

            return BuildMessageResponse.builder()
                    .hexMessage(hex)
                    .mti(request.getMti())
                    .profileId(request.getProfileId())
                    .validationWarnings(warnings)
                    .build();

        } catch (Exception e) {
            throw new RuntimeException("BUILD_FAILED: " + e.getMessage());
        }
    }

    @Override
    public ValidationResponse rerun(String runReference, String userId, String correlationId) {
        // 1. Fetch original run from history-service
        Object raw = historyClient.getRunDetail(runReference);

        // 2. Extract profileId and rawMessage from the history response
        // The history response is a Map — parse it safely
        if (!(raw instanceof java.util.Map<?, ?> map)) {
            throw new RuntimeException("Could not fetch original run: " + runReference);
        }

        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> data = (java.util.Map<String, Object>) map;

        // ApiResponse wraps in "data" field
        Object innerData = data.get("data");
        if (innerData instanceof java.util.Map<?, ?> inner) {
            data = (java.util.Map<String, Object>) inner;
        }

        Object profileIdObj   = data.get("profileId");
        Object rawMessageObj  = data.get("rawMessage");
        Object aiEnabledObj   = data.get("aiEnabled");

        if (profileIdObj == null || rawMessageObj == null) {
            throw new RuntimeException("Original run data incomplete for rerun: " + runReference);
        }

        ValidationRequest rerunRequest = ValidationRequest.builder()
                .profileId(Long.valueOf(profileIdObj.toString()))
                .rawMessage(rawMessageObj.toString())
                .enableAi(Boolean.TRUE.equals(aiEnabledObj))
                .isRerun(true)
                .originalRunReference(runReference)
                .build();

        return validate(rerunRequest, userId, correlationId);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    private String determineStatus(List<ValidationErrorDTO> errors) {
        if (errors.isEmpty()) return "PASSED";
        boolean hasCritical = errors.stream()
                .anyMatch(e -> "CRITICAL".equalsIgnoreCase(e.getSeverity()));
        return hasCritical ? "FAILED" : "WARNED";
    }

    private AiResultDTO callAiExplain(String runRef, Long profileId, String mti,
                                      List<ValidationErrorDTO> errors,
                                      Map<Integer, String> maskedFields,
                                      String correlationId) {
        long start = System.currentTimeMillis();
        try {
            List<AiExplainRequestDto.AiErrorDto> aiErrors = errors.stream()
                    .map(e -> AiExplainRequestDto.AiErrorDto.builder()
                            .deNumber(e.getDeNumber())
                            .fieldName(e.getFieldName())
                            .severity(e.getSeverity())
                            .errorCode(e.getErrorCode())
                            .errorMessage(e.getMessage())
                            .build())
                    .collect(Collectors.toList());

            AiExplainRequestDto req = AiExplainRequestDto.builder()
                    .runReference(runRef)
                    .profileId(profileId)
                    .mti(mti)
                    .errors(aiErrors)
                    .parsedFields(maskedFields)
                    .correlationId(correlationId)
                    .build();

            com.verinite.common.dto.ApiResponse<String> resp = aiServiceClient.explain(req);
            long durationMs = System.currentTimeMillis() - start;

            if (resp != null && resp.getData() != null) {
                return AiResultDTO.builder()
                        .explanation(resp.getData())
                        .durationMs(durationMs)
                        .skipped(false)
                        .build();
            } else {
                return AiResultDTO.builder()
                        .durationMs(durationMs)
                        .skipped(true)
                        .skipReason("AI_UNAVAILABLE")
                        .build();
            }
        } catch (Exception e) {
            log.warn("[AI] explain failed for profileId={} mti={}: {}", profileId, mti, e.getMessage());
            return AiResultDTO.builder()
                    .durationMs(System.currentTimeMillis() - start)
                    .skipped(true)
                    .skipReason("AI_UNAVAILABLE")
                    .build();
        }
    }

    private List<ParsedFieldDTO> buildParsedFieldDTOs(Map<Integer, String> maskedFields,
                                                      Map<Integer, String> rawFields,
                                                      Map<Integer, String> deFieldNames) {
        return maskedFields.entrySet().stream()
                .filter(e -> e.getKey() > 0) // exclude MTI (key=0)
                .sorted(Map.Entry.comparingByKey())
                .map(e -> {
                    int de = e.getKey();
                    return ParsedFieldDTO.builder()
                            .deNumber(de)
                            .fieldName(deFieldNames.getOrDefault(de, "DE" + de))
                            .value(e.getValue())
                            .masked(de == 2 && !Objects.equals(e.getValue(), rawFields.get(de)))
                            .build();
                })
                .collect(Collectors.toList());
    }

    private BitmapDTO computeBitmap(Map<Integer, String> fields) {
        boolean hasSecondary = fields.keySet().stream()
                .anyMatch(k -> k >= 65 && k <= 128);

        long primaryBits = 0L;
        if (hasSecondary) primaryBits |= (1L << 63); // bit 1 signals secondary bitmap present
        for (int de = 2; de <= 64; de++) {
            if (fields.containsKey(de)) {
                primaryBits |= (1L << (64 - de));
            }
        }

        String primary = String.format("%016X", primaryBits);

        String secondary = null;
        if (hasSecondary) {
            long secondaryBits = 0L;
            for (int de = 65; de <= 128; de++) {
                if (fields.containsKey(de)) {
                    secondaryBits |= (1L << (128 - de));
                }
            }
            secondary = String.format("%016X", secondaryBits);
        }

        return BitmapDTO.builder().primary(primary).secondary(secondary).build();
    }

    private String normaliseDeNumber(String deNumber) {
        if (deNumber == null) return "";
        return deNumber.toUpperCase().replace("DE", "").trim();
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }
}