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

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ValidationServiceImpl implements ValidationService {

    private static final Map<String, String> MTI_DESC = Map.of(
            "0100", "Authorization Request",
            "0110", "Authorization Response",
            "0200", "Financial Transaction Request",
            "0210", "Financial Transaction Response",
            "0400", "Reversal Request",
            "0410", "Reversal Response",
            "0800", "Network Management Request",
            "0810", "Network Management Response"
    );

    private final ValidationCacheService cacheService;
    private final PackagerCache          packagerCache;
    private final AIServiceClient        aiServiceClient;
    private final ValidationRunPublisher publisher;
    private final RunReferenceService    runReferenceService;
    private final RulesClient           rulesClient;
    private final HistoryServiceClient  historyClient;

    @Override
    public ValidationResponse validate(ValidationRequest request,
                                       String userId,
                                       String correlationId) {

        long totalStart = System.currentTimeMillis();
        log.debug("[{}] validate: profileId={} enableAi={}", correlationId,
                request.getProfileId(), request.isEnableAi());

        // ── Phase 1: Fetch profile + format ──────────────────────────────────
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

        // ── Phase 2: Load / cache packager ────────────────────────────────────
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

            String runRef  = runReferenceService.generate();
            int    totalMs = (int)(System.currentTimeMillis() - totalStart);

            ValidationRunEvent errorEvent = ValidationRunEvent.builder()
                    .runReference(runRef).status("PARSE_ERROR")
                    .profileId(request.getProfileId()).mti("UNKNOWN")
                    .rawMessage(request.getRawMessage())
                    .userId(userId != null ? userId : "anonymous")
                    .correlationId(correlationId != null ? correlationId : "")
                    .parseDurationMs((int) parseDurationMs)
                    .validationDurationMs(0).totalDurationMs(totalMs)
                    .aiEnabled(request.isEnableAi())
                    .totalErrors(0).criticalCount(0).warningCount(0).infoCount(0)
                    .totalFieldsPresent(0).isRerun(request.isRerun())
                    .originalRunReference(request.getOriginalRunReference())
                    .parsedFields(Collections.emptyList())
                    .errors(Collections.emptyList())
                    .timestamp(LocalDateTime.now())
                    .build();
            publisher.publish(errorEvent);

            return ValidationResponse.builder()
                    .runReference(runRef).status("PARSE_ERROR")
                    .profileId(request.getProfileId())
                    .profile(profileFormat.getProfileName())
                    .mti("UNKNOWN")
                    .message("jPOS: " + e.getMessage())
                    .parsedFields(Collections.emptyList())
                    .errors(Collections.emptyList())
                    .summary(ValidationResponse.Summary.builder()
                            .criticalCount(0).warningCount(0).infoCount(0).build())
                    .timing(TimingDTO.builder()
                            .parseDurationMs(parseDurationMs)
                            .validationDurationMs(0L)
                            .aiDurationMs(0L)
                            .totalDurationMs(totalMs)
                            .build())
                    .ai(AiResultDTO.builder()
                            .enabled(request.isEnableAi())
                            .skipped(true).skipReason("PARSE_ERROR").build())
                    .timestamp(LocalDateTime.now())
                    .build();
        }

        long parseDurationMs = System.currentTimeMillis() - parseStart;
        String mti           = parsed.getMti();
        Map<Integer, String> rawFields    = parsed.getFields();
        Map<Integer, String> maskedFields = PanMaskingUtil.maskFields(rawFields);
        BitmapDTO bitmap = computeBitmap(rawFields);

        // ── Phase 4: Fetch rules ──────────────────────────────────────────────
        List<EffectiveRuleDto> rules;
        try {
            rules = cacheService.getRules(request.getProfileId(), mti);
        } catch (Exception e) {
            log.warn("[{}] Rules service unavailable — empty rules: {}", correlationId, e.getMessage());
            rules = Collections.emptyList();
        }

        Map<Integer, String> deFieldNames = new HashMap<>();
        for (EffectiveRuleDto rule : rules) {
            int de = RulesEngine.parseDeNumber(rule.getDeNumber());
            if (de > 0 && rule.getFieldName() != null) {
                deFieldNames.put(de, rule.getFieldName());
            }
        }

        // ── Phase 5: Rules Engine ─────────────────────────────────────────────
        long validateStart        = System.currentTimeMillis();
        List<ValidationErrorDTO> errors = RulesEngine.evaluate(rawFields, rules);
        long validationDurationMs = System.currentTimeMillis() - validateStart;
        String status             = determineStatus(errors);

        // ── Phase 6: AI Explanation ───────────────────────────────────────────
        AiResultDTO aiResult;
        long aiDurationMs = 0L;
        if (!request.isEnableAi()) {
            aiResult = AiResultDTO.builder()
                    .enabled(false).skipped(true).skipReason("AI_DISABLED").build();
        } else if (errors.isEmpty()) {
            aiResult = AiResultDTO.builder()
                    .enabled(true).skipped(true).skipReason("NO_ERRORS").build();
        } else {
            aiResult = callAiExplain(null, request.getProfileId(), mti,
                    errors, maskedFields, correlationId);
            aiResult = AiResultDTO.builder()
                    .enabled(true)
                    .explanation(aiResult.getExplanation())
                    .modelUsed(aiResult.getModelUsed())
                    .durationMs(aiResult.getDurationMs())
                    .skipped(aiResult.isSkipped())
                    .skipReason(aiResult.getSkipReason())
                    .build();
            aiDurationMs = aiResult.getDurationMs();
        }

        // ── Phase 7: Build event and publish ──────────────────────────────────
        String runRef        = runReferenceService.generate();
        long   totalDurationMs = System.currentTimeMillis() - totalStart;

        List<ParsedFieldDTO> parsedFieldDTOs =
                buildParsedFieldDTOs(maskedFields, rawFields, deFieldNames);

        long criticalCount = errors.stream()
                .filter(e -> "CRITICAL".equalsIgnoreCase(e.getSeverity())).count();
        long warningCount  = errors.stream()
                .filter(e -> "WARNING".equalsIgnoreCase(e.getSeverity())).count();
        long infoCount     = errors.stream()
                .filter(e -> "INFO".equalsIgnoreCase(e.getSeverity())).count();

        String panMasked    = maskedFields.get(2);
        String responseCode = rawFields.get(39);
        String currencyCode = rawFields.get(49);
        String merchantName = rawFields.get(43);
        String terminalId   = rawFields.get(41);
        Long   txnAmount    = parseLong(rawFields.get(4));

        ValidationRunEvent event = ValidationRunEvent.builder()
                .runReference(runRef).status(status)
                .profileId(request.getProfileId())
                .profileNameSnapshot(profileFormat.getProfileName())
                .formatId(profileFormat.getFormatId())
                .mti(mti)
                .mtiDescription(getMtiDescription(mti))
                .rawMessage(request.getRawMessage())
                .userId(userId != null ? userId : "anonymous")
                .correlationId(correlationId != null ? correlationId : "")
                .isRerun(request.isRerun())
                .originalRunReference(request.getOriginalRunReference())
                .bitmapPrimary(bitmap.getPrimary())
                .bitmapExtended(bitmap.getExtended())
                .totalFieldsPresent(rawFields.size())
                .totalFieldsParsed(parsedFieldDTOs.size())
                .totalErrors(errors.size())
                .criticalCount((int) criticalCount)
                .warningCount((int) warningCount)
                .infoCount((int) infoCount)
                .panMasked(panMasked)
                .responseCode(responseCode)
                .currencyCode(currencyCode)
                .merchantName(merchantName)
                .terminalId(terminalId)
                .transactionAmount(txnAmount)
                .parseDurationMs((int) parseDurationMs)
                .validationDurationMs((int) validationDurationMs)
                .totalDurationMs((int) totalDurationMs)
                .aiEnabled(request.isEnableAi())
                .aiDurationMs(aiResult.isSkipped() ? null : (int) aiResult.getDurationMs())
                .aiExplanation(aiResult.getExplanation())
                .aiModelUsed(aiResult.getModelUsed())
                .parsedFields(parsedFieldDTOs.stream()
                        .map(f -> ValidationRunEvent.ParsedFieldEvent.builder()
                                .deNumber("DE" + f.getDeNumber())
                                .fieldName(f.getFieldName())
                                .rawValue(f.getRawValue())
                                .displayValue(f.getDisplayValue())
                                .isPresent(true)
                                .fieldLength(f.getRawValue() != null ? f.getRawValue().length() : 0)
                                .dePosition(f.getDeNumber())
                                .encodingType("FIXED")
                                .build())
                        .collect(Collectors.toList()))
                .errors(errors.stream()
                        .map(e -> ValidationRunEvent.ValidationErrorEvent.builder()
                                .deNumber(e.getDeNumber())
                                .fieldName(e.getFieldName())
                                .severity(e.getSeverity())
                                .errorCode(e.getErrorCode())
                                .errorMessage(e.getIssueDescription())
                                .aiExplanation(e.getAiExplanation())
                                .build())
                        .collect(Collectors.toList()))
                .timestamp(LocalDateTime.now())
                .build();

        publisher.publish(event);

        // ── Phase 8: Return response ──────────────────────────────────────────
        TimingDTO timing = TimingDTO.builder()
                .parseDurationMs(parseDurationMs)
                .validationDurationMs(validationDurationMs)
                .aiDurationMs(aiDurationMs)
                .totalDurationMs(totalDurationMs)
                .build();

        return ValidationResponse.builder()
                .runReference(runRef)
                .status(status)
                .profileId(request.getProfileId())
                .profile(profileFormat.getProfileName())
                .mti(mti)
                .mtiDescription(getMtiDescription(mti))
                .parsedFields(parsedFieldDTOs)
                .errors(errors)
                .summary(ValidationResponse.Summary.builder()
                        .criticalCount((int) criticalCount)
                        .warningCount((int) warningCount)
                        .infoCount((int) infoCount)
                        .build())
                .timing(timing)
                .bitmap(bitmap)
                .ai(aiResult)
                .timestamp(LocalDateTime.now())
                .build();
    }

    @Override
    public BuildMessageResponse buildMessage(BuildMessageRequest request, String userId) {

        ProfileFormatResponseDto profileFormat = cacheService.getProfileFormat(request.getProfileId());
        if (profileFormat == null || profileFormat.getXmlContent() == null) {
            throw new RuntimeException(
                    "PROFILE_NOT_FOUND: No active format for profileId=" + request.getProfileId());
        }

        ISOPackager packager = packagerCache.get(
                profileFormat.getFormatId(), profileFormat.getXmlContent());

        List<FieldDefinitionDto> fieldDefs;
        try {
            fieldDefs = rulesClient.getFieldDefinitions(request.getProfileId(), request.getMti());
        } catch (Exception e) {
            log.warn("Field definitions unavailable — building without validation: {}", e.getMessage());
            fieldDefs = Collections.emptyList();
        }

        Map<String, FieldDefinitionDto> defByDeNum = fieldDefs.stream()
                .filter(fd -> fd.getDeNumber() != null)
                .collect(Collectors.toMap(
                        fd -> fd.getDeNumber().toUpperCase().replace("DE", ""),
                        fd -> fd, (a, b) -> a));

        List<String> warnings          = new ArrayList<>();
        List<String> missingMandatory  = new ArrayList<>();
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
                            + " exceeds maxLength " + fd.getMaxLength() + " — truncated");
                    value = value.substring(0, fd.getMaxLength());
                }
                validatedFields.put(de, value);
            }
        }

        // ── Check for missing mandatory fields ────────────────────────────────
        for (Map.Entry<String, FieldDefinitionDto> entry : defByDeNum.entrySet()) {
            FieldDefinitionDto fd = entry.getValue();
            if (Boolean.TRUE.equals(fd.getIsMandatory())) {
                try {
                    int de = Integer.parseInt(entry.getKey());

                    // FIX: DE1 is the Secondary Bitmap — it is ALWAYS auto-generated
                    // by jPOS when fields 65-128 are present. It is never a user-supplied
                    // field and must never be flagged as missing here.
                    if (de == 1) continue;

                    if (!validatedFields.containsKey(de)) {
                        missingMandatory.add("DE" + de + " (" + fd.getFieldName() + ") is mandatory");
                    }
                } catch (NumberFormatException ignored) {}
            }
        }

        try {
            ISOMsg msg    = IsoParserUtil.buildMsg(request.getMti(), validatedFields, packager);
            byte[] packed  = msg.pack();

            // ── Output format ──────────────────────────────────────────────────────
            boolean asciiMode = "ASCII".equalsIgnoreCase(request.getOutputFormat());
            String  rawMsg    = asciiMode
                    ? new String(packed, StandardCharsets.ISO_8859_1)
                    : bytesToHex(packed);

// ── bitmapHex — production-safe primary + secondary bitmap ──────────────
            boolean hasExtended = validatedFields.keySet().stream()
                    .anyMatch(de -> de >= 65 && de <= 128);

            long primaryBits = 0L;

            if (hasExtended) {
                // Bit 1 indicates secondary bitmap exists
                primaryBits |= (1L << 63);
            }

            for (int de = 2; de <= 64; de++) {
                if (validatedFields.containsKey(de)) {
                    primaryBits |= (1L << (64 - de));
                }
            }

            String primaryBitmap = String.format("%016X", primaryBits);

            String bitmapHex;

            if (hasExtended) {
                long secondaryBits = 0L;

                for (int de = 65; de <= 128; de++) {
                    if (validatedFields.containsKey(de)) {
                        secondaryBits |= (1L << (128 - de));
                    }
                }

                String secondaryBitmap = String.format("%016X", secondaryBits);

                // Full 128-bit bitmap (32 hex chars)
                bitmapHex = primaryBitmap + secondaryBitmap;
            } else {
                bitmapHex = primaryBitmap;
            }

            // ── Field breakdown ────────────────────────────────────────────────────
            List<BuildMessageResponse.FieldBreakdown> breakdown = validatedFields.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .map(e -> {
                        String deKey = String.valueOf(e.getKey());
                        FieldDefinitionDto fd = defByDeNum.get(deKey);
                        return BuildMessageResponse.FieldBreakdown.builder()
                                .deNumber(e.getKey())
                                .fieldName(fd != null ? fd.getFieldName() : "DE" + e.getKey())
                                .rawValue(e.getValue())
                                .encoding(fd != null && fd.getLengthType() != null
                                        ? fd.getLengthType() : "FIXED")
                                .build();
                    })
                    .collect(Collectors.toList());

            List<Integer> bitsSet = new ArrayList<>(validatedFields.keySet());
            Collections.sort(bitsSet);

            return BuildMessageResponse.builder()
                    .rawMessage(rawMsg)
                    .outputFormat(asciiMode ? "ASCII" : "HEX")
                    .mti(request.getMti())
                    .mtiDescription(getMtiDescription(request.getMti()))
                    .profileId(request.getProfileId())
                    .profile(profileFormat.getProfileName())
                    .bitmapHex(bitmapHex)
                    .bitsSet(bitsSet)
                    .totalLength(packed.length)
                    .fieldBreakdown(breakdown)
                    .missingMandatory(missingMandatory)
                    .validationWarnings(warnings)
                    .build();

        } catch (Exception e) {
            throw new RuntimeException("BUILD_FAILED: " + e.getMessage());
        }
    }

    @Override
    public ValidationResponse rerun(String runReference, String userId, String correlationId) {
        Object raw = historyClient.getRunDetail(runReference);

        if (!(raw instanceof Map<?, ?> map)) {
            throw new RuntimeException("Could not fetch original run: " + runReference);
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) map;

        Object innerData = data.get("data");
        if (innerData instanceof Map<?, ?> inner) {
            //noinspection unchecked
            data = (Map<String, Object>) inner;
        }

        Object profileIdObj  = data.get("profileId");
        Object rawMessageObj = data.get("rawMessage");
        Object aiEnabledObj  = data.get("aiEnabled");

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

    // ── Private helpers ───────────────────────────────────────────────────────

    private String getMtiDescription(String mti) {
        return MTI_DESC.getOrDefault(mti, mti + " – Unknown Message Type");
    }

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
                            .errorMessage(e.getIssueDescription())
                            .build())
                    .collect(Collectors.toList());

            AiExplainRequestDto req = AiExplainRequestDto.builder()
                    .runReference(runRef).profileId(profileId).mti(mti)
                    .errors(aiErrors).parsedFields(maskedFields).correlationId(correlationId)
                    .build();

            com.verinite.common.dto.ApiResponse<String> resp = aiServiceClient.explain(req);
            long durationMs = System.currentTimeMillis() - start;

            if (resp != null && resp.getData() != null) {
                return AiResultDTO.builder()
                        .enabled(true)
                        .explanation(resp.getData())
                        .durationMs(durationMs)
                        .skipped(false)
                        .build();
            }
            return AiResultDTO.builder()
                    .enabled(true).durationMs(durationMs)
                    .skipped(true).skipReason("AI_UNAVAILABLE").build();
        } catch (Exception e) {
            log.warn("[AI] explain failed profileId={} mti={}: {}", profileId, mti, e.getMessage());
            return AiResultDTO.builder()
                    .enabled(true)
                    .durationMs(System.currentTimeMillis() - start)
                    .skipped(true).skipReason("AI_UNAVAILABLE").build();
        }
    }

    private List<ParsedFieldDTO> buildParsedFieldDTOs(Map<Integer, String> maskedFields,
                                                      Map<Integer, String> rawFields,
                                                      Map<Integer, String> deFieldNames) {
        return maskedFields.entrySet().stream()
                .filter(e -> e.getKey() > 0)
                .sorted(Map.Entry.comparingByKey())
                .map(e -> {
                    int    de           = e.getKey();
                    String maskedValue  = e.getValue();
                    String raw          = rawFields.get(de);
                    boolean isMasked    = de == 2 && !Objects.equals(maskedValue, raw);
                    return ParsedFieldDTO.builder()
                            .deNumber(de)
                            .fieldName(deFieldNames.getOrDefault(de, "DE" + de))
                            .rawValue(maskedValue)
                            .displayValue(maskedValue)
                            .isPresent(true)
                            .masked(isMasked)
                            .build();
                })
                .collect(Collectors.toList());
    }

    private BitmapDTO computeBitmap(Map<Integer, String> fields) {
        boolean hasExtended = fields.keySet().stream().anyMatch(k -> k >= 65 && k <= 128);
        long primaryBits = 0L;
        if (hasExtended) primaryBits |= (1L << 63);

        List<Integer> bitsSet = new ArrayList<>();

        for (int de = 2; de <= 64; de++) {
            if (fields.containsKey(de)) {
                primaryBits |= (1L << (64 - de));
                bitsSet.add(de);
            }
        }
        String primary = String.format("%016X", primaryBits);

        String extended = null;
        if (hasExtended) {
            long secondaryBits = 0L;
            for (int de = 65; de <= 128; de++) {
                if (fields.containsKey(de)) {
                    secondaryBits |= (1L << (128 - de));
                    bitsSet.add(de);
                }
            }
            extended = String.format("%016X", secondaryBits);
        }
        Collections.sort(bitsSet);

        return BitmapDTO.builder()
                .primary(primary)
                .extended(extended)
                .bitsSet(bitsSet)
                .build();
    }

    private Long parseLong(String val) {
        if (val == null) return null;
        try {
            String stripped = val.replaceAll("^0+", "");
            return Long.parseLong(stripped.isBlank() ? "0" : stripped);
        } catch (NumberFormatException e) { return null; }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format("%02X", b));
        return sb.toString();
    }
}