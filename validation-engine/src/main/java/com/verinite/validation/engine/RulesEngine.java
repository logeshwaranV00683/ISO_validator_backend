package com.verinite.validation.engine;

import com.verinite.validation.dto.AllowedValueDto;
import com.verinite.validation.dto.EffectiveRuleDto;
import com.verinite.validation.dto.ValidationErrorDTO;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Pure stateless rules engine — NO Spring beans, NO DB, NO I/O.
 *
 * Evaluation order per rule (priority-sorted by rules-service):
 *   1. MANDATORY check  → if fails, skip remaining checks for that DE
 *   2. EXACT_LENGTH
 *   3. MIN_LENGTH
 *   4. MAX_LENGTH
 *   5. REGEX
 *   6. ALLOWED_VALUES
 *
 * Returns List<ValidationErrorDTO> — empty = PASSED.
 */
@Slf4j
public class RulesEngine {

    private RulesEngine() { /* static utility */ }

    public static List<ValidationErrorDTO> evaluate(
            Map<Integer, String> parsedFields,
            List<EffectiveRuleDto> rules) {

        List<ValidationErrorDTO> errors = new ArrayList<>();

        for (EffectiveRuleDto rule : rules) {

            int deNumber = parseDeNumber(rule.getDeNumber());
            if (deNumber < 0) {
                log.warn("RulesEngine: skipping rule id={} — unparseable deNumber='{}'",
                        rule.getId(), rule.getDeNumber());
                continue;
            }

            String fieldValue = parsedFields.get(deNumber);
            String deRef      = "DE" + deNumber;
            String fieldName  = rule.getFieldName() != null ? rule.getFieldName() : deRef;
            String severity   = normaliseSeverity(rule.getSeverity());

            // ── 1. MANDATORY ─────────────────────────────────────────────
            if (Boolean.TRUE.equals(rule.getIsMandatory())) {
                if (fieldValue == null || fieldValue.isBlank()) {
                    errors.add(buildError(severity, "MANDATORY", deNumber, deRef, fieldName,
                            deRef + " (" + fieldName + ") is mandatory but missing or blank"));
                    continue; // no point checking other constraints if field absent
                }
            }

            // Field absent & not mandatory → skip remaining checks
            if (fieldValue == null || fieldValue.isBlank()) {
                continue;
            }

            // ── 2. EXACT_LENGTH ──────────────────────────────────────────
            if (rule.getExactLength() != null) {
                if (fieldValue.length() != rule.getExactLength()) {
                    errors.add(buildError(severity, "EXACT_LENGTH", deNumber, deRef, fieldName,
                            deRef + " (" + fieldName + ") must be exactly "
                                    + rule.getExactLength() + " chars (actual: "
                                    + fieldValue.length() + ")"));
                }
            }

            // ── 3. MIN_LENGTH ────────────────────────────────────────────
            if (rule.getMinLength() != null) {
                if (fieldValue.length() < rule.getMinLength()) {
                    errors.add(buildError(severity, "MIN_LENGTH", deNumber, deRef, fieldName,
                            deRef + " (" + fieldName + ") below min length "
                                    + rule.getMinLength() + " (actual: "
                                    + fieldValue.length() + ")"));
                }
            }

            // ── 4. MAX_LENGTH ────────────────────────────────────────────
            if (rule.getMaxLength() != null) {
                if (fieldValue.length() > rule.getMaxLength()) {
                    errors.add(buildError(severity, "MAX_LENGTH", deNumber, deRef, fieldName,
                            deRef + " (" + fieldName + ") exceeds max length "
                                    + rule.getMaxLength() + " (actual: "
                                    + fieldValue.length() + ")"));
                }
            }

            // ── 5. REGEX ─────────────────────────────────────────────────
            if (rule.getPatternRegex() != null && !rule.getPatternRegex().isBlank()) {
                try {
                    if (!fieldValue.matches(rule.getPatternRegex())) {
                        errors.add(buildError(severity, "REGEX", deNumber, deRef, fieldName,
                                deRef + " (" + fieldName + ") does not match pattern: "
                                        + rule.getPatternRegex()));
                    }
                } catch (Exception ex) {
                    log.warn("RulesEngine: invalid regex in rule id={} deNumber={} pattern='{}': {}",
                            rule.getId(), rule.getDeNumber(), rule.getPatternRegex(), ex.getMessage());
                }
            }

            // ── 6. ALLOWED_VALUES ─────────────────────────────────────────
            List<AllowedValueDto> allowedValueDtos = rule.getAllowedValues();
            if (allowedValueDtos != null && !allowedValueDtos.isEmpty()) {
                Set<String> allowed = allowedValueDtos.stream()
                        .filter(v -> v.getAllowedValue() != null)
                        .map(v -> v.getAllowedValue().trim())
                        .collect(Collectors.toSet());

                if (!allowed.isEmpty() && !allowed.contains(fieldValue.trim())) {
                    errors.add(buildError(severity, "ALLOWED_VALUES", deNumber, deRef, fieldName,
                            deRef + " (" + fieldName + ") value '" + fieldValue
                                    + "' not in allowed values: " + String.join(", ", allowed)));
                }
            }
        }

        return errors;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Parse DE number from "DE2", "de2", or "2".
     * Returns -1 on failure.
     */
    public static int parseDeNumber(String raw) {
        if (raw == null) return -1;
        try {
            return Integer.parseInt(
                    raw.toUpperCase().replace("DE", "").trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static String normaliseSeverity(String raw) {
        if (raw == null) return "CRITICAL";
        return switch (raw.toUpperCase()) {
            case "WARNING", "WARN" -> "WARNING";
            case "INFO"            -> "INFO";
            default                -> "CRITICAL";
        };
    }

    private static ValidationErrorDTO buildError(
            String severity, String ruleType,
            int deNumber, String deRef, String fieldName,
            String message) {
        return ValidationErrorDTO.builder()
                .severity(severity)
                .errorCode("ERR-" + deRef + "-" + ruleType)
                .deNumber(deRef)
                .deNumberInt(deNumber)
                .fieldName(fieldName)
                .issueDescription(message)   // renamed from .message()
                .ruleSnapshot(deRef + " " + ruleType + " rule")
                .build();
    }
}