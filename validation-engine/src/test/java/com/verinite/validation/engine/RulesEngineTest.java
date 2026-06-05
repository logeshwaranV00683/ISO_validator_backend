package com.verinite.validation.engine;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class RulesEngineTest {

    // ─── MANDATORY ────────────────────────────────────────

    @Test
    void mandatory_FieldPresent_NoError() {
        Map<Integer, String> fields = Map.of(2, "4532123456781234");
        List<Map<String, String>> rules = List.of(
                Map.of("ruleType", "MANDATORY",
                        "deField", "DE2", "ruleValue", ""));

        List<String> errors = RulesEngine.evaluate(fields, rules);
        assertThat(errors).isEmpty();
    }

    @Test
    void mandatory_FieldMissing_ReturnsError() {
        Map<Integer, String> fields = new HashMap<>();
        List<Map<String, String>> rules = List.of(
                Map.of("ruleType", "MANDATORY",
                        "deField", "DE2", "ruleValue", ""));

        List<String> errors = RulesEngine.evaluate(fields, rules);
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0)).contains("DE2")
                .contains("mandatory");
    }

    // ─── REGEX ────────────────────────────────────────────

    @Test
    void regex_FieldMatchesPattern_NoError() {
        Map<Integer, String> fields = Map.of(3, "000000");
        List<Map<String, String>> rules = List.of(
                Map.of("ruleType", "REGEX",
                        "deField", "DE3", "ruleValue", "\\d{6}"));

        List<String> errors = RulesEngine.evaluate(fields, rules);
        assertThat(errors).isEmpty();
    }

    @Test
    void regex_FieldNotMatchesPattern_ReturnsError() {
        Map<Integer, String> fields = Map.of(3, "ABC123");
        List<Map<String, String>> rules = List.of(
                Map.of("ruleType", "REGEX",
                        "deField", "DE3", "ruleValue", "\\d{6}"));

        List<String> errors = RulesEngine.evaluate(fields, rules);
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0)).contains("DE3");
    }

    // ─── ALLOWED_VALUES ───────────────────────────────────

    @Test
    void allowedValues_ValueAllowed_NoError() {
        Map<Integer, String> fields = Map.of(49, "356");
        List<Map<String, String>> rules = List.of(
                Map.of("ruleType", "ALLOWED_VALUES",
                        "deField", "DE49", "ruleValue", "356,840,978"));

        List<String> errors = RulesEngine.evaluate(fields, rules);
        assertThat(errors).isEmpty();
    }

    @Test
    void allowedValues_ValueNotAllowed_ReturnsError() {
        Map<Integer, String> fields = Map.of(49, "999");
        List<Map<String, String>> rules = List.of(
                Map.of("ruleType", "ALLOWED_VALUES",
                        "deField", "DE49", "ruleValue", "356,840,978"));

        List<String> errors = RulesEngine.evaluate(fields, rules);
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0)).contains("DE49");
    }

    // ─── MAX_LENGTH ───────────────────────────────────────

    @Test
    void maxLength_WithinLimit_NoError() {
        Map<Integer, String> fields = Map.of(41, "12345678");
        List<Map<String, String>> rules = List.of(
                Map.of("ruleType", "MAX_LENGTH",
                        "deField", "DE41", "ruleValue", "8"));

        List<String> errors = RulesEngine.evaluate(fields, rules);
        assertThat(errors).isEmpty();
    }

    @Test
    void maxLength_ExceedsLimit_ReturnsError() {
        Map<Integer, String> fields = Map.of(41, "123456789");
        List<Map<String, String>> rules = List.of(
                Map.of("ruleType", "MAX_LENGTH",
                        "deField", "DE41", "ruleValue", "8"));

        List<String> errors = RulesEngine.evaluate(fields, rules);
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0)).contains("DE41")
                .contains("max length");
    }

    // ─── MIN_LENGTH ───────────────────────────────────────

    @Test
    void minLength_AboveMinimum_NoError() {
        Map<Integer, String> fields = Map.of(11, "123456");
        List<Map<String, String>> rules = List.of(
                Map.of("ruleType", "MIN_LENGTH",
                        "deField", "DE11", "ruleValue", "6"));

        List<String> errors = RulesEngine.evaluate(fields, rules);
        assertThat(errors).isEmpty();
    }

    @Test
    void minLength_BelowMinimum_ReturnsError() {
        Map<Integer, String> fields = Map.of(11, "123");
        List<Map<String, String>> rules = List.of(
                Map.of("ruleType", "MIN_LENGTH",
                        "deField", "DE11", "ruleValue", "6"));

        List<String> errors = RulesEngine.evaluate(fields, rules);
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0)).contains("DE11")
                .contains("min length");
    }

    // ─── MULTIPLE RULES ───────────────────────────────────

    @Test
    void multipleRules_AllPass_NoErrors() {
        Map<Integer, String> fields = Map.of(
                2, "4532123456781234",
                3, "000000",
                4, "000000010000");

        List<Map<String, String>> rules = List.of(
                Map.of("ruleType", "MANDATORY",
                        "deField", "DE2", "ruleValue", ""),
                Map.of("ruleType", "REGEX",
                        "deField", "DE3", "ruleValue", "\\d{6}"),
                Map.of("ruleType", "MANDATORY",
                        "deField", "DE4", "ruleValue", ""));

        List<String> errors = RulesEngine.evaluate(fields, rules);
        assertThat(errors).isEmpty();
    }

    @Test
    void multipleRules_SomeFail_ReturnsMultipleErrors() {
        Map<Integer, String> fields = Map.of(3, "ABC");

        List<Map<String, String>> rules = List.of(
                Map.of("ruleType", "MANDATORY",
                        "deField", "DE2", "ruleValue", ""),
                Map.of("ruleType", "REGEX",
                        "deField", "DE3", "ruleValue", "\\d{6}"));

        List<String> errors = RulesEngine.evaluate(fields, rules);
        assertThat(errors).hasSize(2);
    }
}