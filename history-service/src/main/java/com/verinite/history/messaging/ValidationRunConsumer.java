package com.verinite.history.messaging;

import com.verinite.common.enums.EncodingType;
import com.verinite.common.enums.RunStatus;
import com.verinite.history.entity.ValidationRun;
import com.verinite.history.entity.ValidationRunError;
import com.verinite.history.entity.ValidationRunField;
import com.verinite.history.repository.ValidationRunRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import com.verinite.common.enums.Severity;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class ValidationRunConsumer {

    private final ValidationRunRepository validationRunRepository;

    @RabbitListener(queues = "history.validation-runs")
    @Transactional
    public void consumeValidationRun(Map<String, Object> event) {
        String runReference = (String) event.get("runReference");

        if (runReference == null) {
            log.warn("Received validation run event with null runReference — skipping");
            return;
        }

        // Idempotency: never double-insert the same run
        if (validationRunRepository.existsByRunReference(runReference)) {
            log.warn("Duplicate run event for runReference={} — skipping", runReference);
            return;
        }

        log.info("Consuming validation run event: runReference={}", runReference);

        try {
            // FIX: rawMessage was never mapped — caused NOT NULL constraint violation on INSERT
            String rawMessage = (String) event.get("rawMessage");
            if (rawMessage == null) rawMessage = "";   // schema NOT NULL — use empty string as fallback

            ValidationRun run = ValidationRun.builder()
                    .runReference(runReference)
                    .rawMessage(rawMessage)              // FIX: now mapped
                    .profileId(toLong(event.get("profileId")))
                    .profileNameSnapshot((String) event.get("profileNameSnapshot"))
                    .formatId(toLong(event.get("formatId")))
                    .formatNameSnapshot((String) event.get("formatNameSnapshot"))
                    .userId(toLong(event.get("userId")))
                    .usernameSnapshot((String) event.get("usernameSnapshot"))
                    .userRoleSnapshot((String) event.get("userRoleSnapshot"))
                    .mti((String) event.get("mti"))
                    .mtiDescription((String) event.get("mtiDescription"))
                    .bitmapPrimary((String) event.get("bitmapPrimary"))
                    .bitmapExtended((String) event.get("bitmapExtended"))
                    .status(parseStatus((String) event.get("status")))
                    .totalFieldsPresent(toInt(event.get("totalFieldsPresent"), 0))
                    .totalErrors(toInt(event.get("totalErrors"), 0))
                    .criticalCount(toInt(event.get("criticalCount"), 0))
                    .warningCount(toInt(event.get("warningCount"), 0))
                    .infoCount(toInt(event.get("infoCount"), 0))
                    .responseCode((String) event.get("responseCode"))
                    .responseLabel((String) event.get("responseLabel"))
                    .transactionAmount(toLong(event.get("transactionAmount")))
                    .currencyCode((String) event.get("currencyCode"))
                    .merchantName((String) event.get("merchantName"))
                    .terminalId((String) event.get("terminalId"))
                    .panMasked((String) event.get("panMasked"))
                    .parseDurationMs(toInt(event.get("parseDurationMs"), null))
                    .validationDurationMs(toInt(event.get("validationDurationMs"), null))
                    .aiDurationMs(toInt(event.get("aiDurationMs"), null))
                    .totalDurationMs(toInt(event.get("totalDurationMs"), null))
                    .aiEnabled(toBoolean(event.get("aiEnabled"), false))
                    .aiExplanation((String) event.get("aiExplanation"))
                    .aiModelUsed((String) event.get("aiModelUsed"))
                    .isRerun(toBoolean(event.get("isRerun"), false))
                    .originalRunReference((String) event.get("originalRunReference"))
                    .clientIp((String) event.get("clientIp"))
                    .correlationId((String) event.get("correlationId"))
                    .build();

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> parsedFields =
                    (List<Map<String, Object>>) event.get("parsedFields");
            if (parsedFields != null) {
                for (Map<String, Object> f : parsedFields) {
                    ValidationRunField field = ValidationRunField.builder()
                            .runId(run)
                            .deNumber((String) f.get("deNumber"))
                            .fieldName((String) f.get("fieldName"))
                            .rawValue((String) f.get("rawValue"))
                            .displayValue((String) f.get("displayValue"))
                            .isPresent(toBoolean(f.get("isPresent"), false))
                            .fieldLength(toInt(f.get("fieldLength"), null))
                            .dePosition(toInt(f.get("dePosition"), null))
                            .encodingType(parseEncodingType((String) f.get("encodingType")))
                            .build();
                    run.getFields().add(field);
                }
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> errors =
                    (List<Map<String, Object>>) event.get("errors");
            if (errors != null) {
                for (Map<String, Object> e : errors) {
                    ValidationRunError error = ValidationRunError.builder()
                            .runId(run)
                            .ruleId(toLong(e.get("ruleId")))
                            .deNumber((String) e.get("deNumber"))
                            .fieldName((String) e.get("fieldName"))
                            .severity(parseSeverity((String) e.get("severity")))
                            .errorCode((String) e.get("errorCode"))
                            .errorMessage((String) e.get("errorMessage"))
                            .ruleSnapshot((String) e.get("ruleSnapshot"))
                            .expectedValue((String) e.get("expectedValue"))
                            .actualValue((String) e.get("actualValue"))
                            .aiExplanation((String) e.get("aiExplanation"))
                            .aiFixSuggestion((String) e.get("aiFixSuggestion"))
                            .build();
                    run.getErrors().add(error);
                }
            }

            // Single save — cascades to fields + errors
            validationRunRepository.save(run);

            log.info("Saved: runReference={} status={} fields={} errors={}",
                    runReference, run.getStatus(),
                    run.getFields().size(), run.getErrors().size());

        } catch (Exception ex) {
            log.error("Failed to persist runReference={}: {}", runReference, ex.getMessage(), ex);
            throw ex;
        }
    }

    // ── Conversion helpers ────────────────────────────────────────────────────

    private Long toLong(Object val) {
        if (val == null) return null;
        if (val instanceof Long)    return (Long) val;
        if (val instanceof Integer) return ((Integer) val).longValue();
        if (val instanceof Number)  return ((Number) val).longValue();
        try { return Long.parseLong(val.toString()); } catch (Exception e) { return null; }
    }

    private Integer toInt(Object val, Integer defaultVal) {
        if (val == null) return defaultVal;
        if (val instanceof Integer) return (Integer) val;
        if (val instanceof Number)  return ((Number) val).intValue();
        try { return Integer.parseInt(val.toString()); } catch (Exception e) { return defaultVal; }
    }

    private Boolean toBoolean(Object val, Boolean defaultVal) {
        if (val == null) return defaultVal;
        if (val instanceof Boolean) return (Boolean) val;
        return Boolean.parseBoolean(val.toString());
    }

    private RunStatus parseStatus(String val) {
        if (val == null) return RunStatus.PARSE_ERROR;
        try { return RunStatus.valueOf(val); }
        catch (Exception e) { return RunStatus.PARSE_ERROR; }
    }

    private Severity parseSeverity(String val) {
        if (val == null) return Severity.INFO;
        try { return Severity.valueOf(val); }
        catch (Exception e) { return Severity.INFO; }
    }

    private EncodingType parseEncodingType(String val) {
        if (val == null) return null;
        try { return EncodingType.valueOf(val); }
        catch (Exception e) { return null; }
    }
}