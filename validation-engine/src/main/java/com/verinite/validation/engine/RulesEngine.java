package com.verinite.validation.engine;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
public class RulesEngine {

    // Rule types
    public static final String MANDATORY = "MANDATORY";
    public static final String REGEX = "REGEX";
    public static final String ALLOWED_VALUES = "ALLOWED_VALUES";
    public static final String MAX_LENGTH = "MAX_LENGTH";
    public static final String MIN_LENGTH = "MIN_LENGTH";

    /**
     * Evaluate all rules against parsed DE fields
     *
     * @param parsedFields - Map of DE number → value
     * @param rules        - List of rules to evaluate
     * @return List of error messages (empty = VALID)
     */
    public static List<String> evaluate(
            Map<Integer, String> parsedFields,
            List<Map<String, String>> rules) {

        List<String> errors = new ArrayList<>();

        for (Map<String, String> rule : rules) {
            String ruleType = rule.get("ruleType");
            String deField = rule.get("deField");
            String ruleValue = rule.get("ruleValue");

            int deNumber;
            try {
                deNumber = Integer.parseInt(deField.replace("DE", ""));
            } catch (NumberFormatException e) {
                log.warn("Invalid DE field: {}", deField);
                continue;
            }

            String fieldValue = parsedFields.get(deNumber);

            switch (ruleType) {
                case MANDATORY -> {
                    if (fieldValue == null || fieldValue.isBlank()) {
                        errors.add(deField + " is mandatory but missing");
                    }
                }
                case REGEX -> {
                    if (fieldValue != null && !fieldValue.isBlank()) {
                        if (!fieldValue.matches(ruleValue)) {
                            errors.add(deField + " does not match pattern: "
                                    + ruleValue);
                        }
                    }
                }
                case ALLOWED_VALUES -> {
                    if (fieldValue != null && !fieldValue.isBlank()) {
                        String[] allowed = ruleValue.split(",");
                        boolean found = false;
                        for (String val : allowed) {
                            if (val.trim().equals(fieldValue.trim())) {
                                found = true;
                                break;
                            }
                        }
                        if (!found) {
                            errors.add(deField + " value '" + fieldValue
                                    + "' not in allowed values: " + ruleValue);
                        }
                    }
                }
                case MAX_LENGTH -> {
                    if (fieldValue != null) {
                        int max = Integer.parseInt(ruleValue);
                        if (fieldValue.length() > max) {
                            errors.add(deField + " exceeds max length "
                                    + max + " (actual: "
                                    + fieldValue.length() + ")");
                        }
                    }
                }
                case MIN_LENGTH -> {
                    if (fieldValue != null) {
                        int min = Integer.parseInt(ruleValue);
                        if (fieldValue.length() < min) {
                            errors.add(deField + " below min length "
                                    + min + " (actual: "
                                    + fieldValue.length() + ")");
                        }
                    }
                }
                default -> log.warn("Unknown rule type: {}", ruleType);
            }
        }

        return errors;
    }
}