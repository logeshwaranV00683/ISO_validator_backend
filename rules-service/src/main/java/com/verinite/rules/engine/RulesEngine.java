package com.verinite.rules.engine;

import com.verinite.common.enums.DataType;
import com.verinite.common.enums.Severity;
import com.verinite.rules.dto.RuleDto;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Pure stateless rules evaluator — no Spring beans, no DB, no I/O.
 * Identical logic to validation-engine's RulesEngine.
 * Used here for dry-run validation (before saving a rule set).
 * Input: fieldValues map (DE number → raw string value)
 * Output: list of error descriptions
 */
@Slf4j
public class RulesEngine {

    private RulesEngine() {}

    public record EvalResult(String deNumber, String fieldName,
                             Severity severity, String errorCode, String description) {}

    /**
     * Evaluate a set of rules against provided field values.
     *
     * @param fieldValues map of DE number (e.g. "DE2") → raw field value
     * @param rules       active rules for this profile+MTI
     * @return list of validation errors (empty = all passed)
     */
    public static List<EvalResult> evaluate(Map<String, String> fieldValues,
                                            List<RuleDto> rules) {
        List<EvalResult> errors = new ArrayList<>();

        for (RuleDto rule : rules) {
            String deNumber = rule.getDeNumber();
            String value    = fieldValues.get(deNumber);
            boolean present = value != null && !value.isEmpty();

            // Check 1: MANDATORY_ABSENT
            if (Boolean.TRUE.equals(rule.getIsMandatory()) && !present) {
                errors.add(new EvalResult(deNumber, rule.getFieldName(),
                        rule.getSeverity(), "MANDATORY_ABSENT",
                        deNumber + " is mandatory but absent"));
                continue; // no further checks if field is absent
            }

            if (!present) continue; // optional and absent — skip remaining checks

            int length = value.length();

            // Check 2: LENGTH_TOO_SHORT
            if (rule.getMinLength() != null && length < rule.getMinLength()) {
                errors.add(new EvalResult(deNumber, rule.getFieldName(),
                        rule.getSeverity(), "LENGTH_TOO_SHORT",
                        deNumber + " length " + length + " < min " + rule.getMinLength()));
            }

            // Check 3: LENGTH_TOO_LONG
            if (rule.getMaxLength() != null && length > rule.getMaxLength()) {
                errors.add(new EvalResult(deNumber, rule.getFieldName(),
                        rule.getSeverity(), "LENGTH_TOO_LONG",
                        deNumber + " length " + length + " > max " + rule.getMaxLength()));
            }

            // Check 4: TYPE_MISMATCH
            if (rule.getDataType() != null) {
                boolean typeFail = switch (rule.getDataType()) {
                    case DataType.numeric -> !value.matches("\\d+");
                    case DataType.alpha        -> !value.matches("[a-zA-Z]+");
                    case DataType.alphanumeric  -> !value.matches("[a-zA-Z0-9]+");
                    default -> false;
                };
                if (typeFail) {
                    errors.add(new EvalResult(deNumber, rule.getFieldName(),
                            rule.getSeverity(), "TYPE_MISMATCH",
                            deNumber + " value '" + value + "' does not match type " + rule.getDataType()));
                }
            }

            // Check 5: PATTERN_MISMATCH
            if (rule.getPatternRegex() != null && !rule.getPatternRegex().isBlank()) {
                try {
                    if (!Pattern.matches(rule.getPatternRegex(), value)) {
                        errors.add(new EvalResult(deNumber, rule.getFieldName(),
                                rule.getSeverity(), "PATTERN_MISMATCH",
                                deNumber + " value '" + value + "' does not match pattern"));
                    }
                } catch (Exception e) {
                    log.warn("Invalid regex for rule deNumber={}: {}", deNumber, e.getMessage());
                }
            }

            // Check 6: VALUE_NOT_ALLOWED
            if (rule.getAllowedValues() != null && !rule.getAllowedValues().isEmpty()) {
                boolean allowed = rule.getAllowedValues().stream()
                        .anyMatch(av -> av.equals(value));
                if (!allowed) {
                    errors.add(new EvalResult(deNumber, rule.getFieldName(),
                            rule.getSeverity(), "VALUE_NOT_ALLOWED",
                            deNumber + " value '" + value + "' not in allowed set"));
                }
            }
        }

        return errors;
    }
}