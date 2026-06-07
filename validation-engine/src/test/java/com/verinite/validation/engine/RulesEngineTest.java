package com.verinite.validation.engine;

import com.verinite.validation.dto.AllowedValueDto;
import com.verinite.validation.dto.EffectiveRuleDto;
import com.verinite.validation.dto.ValidationErrorDTO;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RulesEngineTest {

    // ── helpers ───────────────────────────────────────────────────────────────

    private EffectiveRuleDto rule(String deNumber, String fieldName) {
        EffectiveRuleDto r = new EffectiveRuleDto();
        r.setDeNumber(deNumber);
        r.setFieldName(fieldName);
        r.setSeverity("CRITICAL");
        return r;
    }

    private AllowedValueDto av(String val) {
        AllowedValueDto a = new AllowedValueDto();
        a.setAllowedValue(val);
        return a;
    }

    // ── MANDATORY ─────────────────────────────────────────────────────────────

    @Test
    void mandatory_fieldPresent_noError() {
        EffectiveRuleDto r = rule("DE3", "Processing Code");
        r.setIsMandatory(true);
        Map<Integer, String> fields = Map.of(3, "000000");

        List<ValidationErrorDTO> errors = RulesEngine.evaluate(fields, List.of(r));
        assertThat(errors).isEmpty();
    }

    @Test
    void mandatory_fieldMissing_oneError() {
        EffectiveRuleDto r = rule("DE3", "Processing Code");
        r.setIsMandatory(true);
        Map<Integer, String> fields = Map.of(); // DE3 absent

        List<ValidationErrorDTO> errors = RulesEngine.evaluate(fields, List.of(r));
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).getSeverity()).isEqualTo("CRITICAL");
        assertThat(errors.get(0).getErrorCode()).isEqualTo("ERR-DE3-MANDATORY");
    }

    @Test
    void mandatory_fieldBlank_oneError() {
        EffectiveRuleDto r = rule("DE3", "Processing Code");
        r.setIsMandatory(true);
        Map<Integer, String> fields = Map.of(3, "  ");

        List<ValidationErrorDTO> errors = RulesEngine.evaluate(fields, List.of(r));
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).getErrorCode()).isEqualTo("ERR-DE3-MANDATORY");
    }

    // ── MAX_LENGTH ────────────────────────────────────────────────────────────

    @Test
    void maxLength_withinLimit_noError() {
        EffectiveRuleDto r = rule("DE4", "Transaction Amount");
        r.setMaxLength(12);
        Map<Integer, String> fields = Map.of(4, "000000010000"); // 12 chars

        assertThat(RulesEngine.evaluate(fields, List.of(r))).isEmpty();
    }

    @Test
    void maxLength_exceeded_oneError() {
        EffectiveRuleDto r = rule("DE4", "Transaction Amount");
        r.setMaxLength(12);
        Map<Integer, String> fields = Map.of(4, "0000000100001"); // 13 chars

        List<ValidationErrorDTO> errors = RulesEngine.evaluate(fields, List.of(r));
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).getErrorCode()).isEqualTo("ERR-DE4-MAX_LENGTH");
    }

    // ── MIN_LENGTH ────────────────────────────────────────────────────────────

    @Test
    void minLength_tooShort_oneError() {
        EffectiveRuleDto r = rule("DE11", "STAN");
        r.setMinLength(6);
        Map<Integer, String> fields = Map.of(11, "123"); // 3 chars

        List<ValidationErrorDTO> errors = RulesEngine.evaluate(fields, List.of(r));
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).getErrorCode()).isEqualTo("ERR-DE11-MIN_LENGTH");
    }

    // ── EXACT_LENGTH ──────────────────────────────────────────────────────────

    @Test
    void exactLength_wrongLength_oneError() {
        EffectiveRuleDto r = rule("DE3", "Processing Code");
        r.setExactLength(6);
        Map<Integer, String> fields = Map.of(3, "0000"); // 4 chars

        List<ValidationErrorDTO> errors = RulesEngine.evaluate(fields, List.of(r));
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).getErrorCode()).isEqualTo("ERR-DE3-EXACT_LENGTH");
    }

    // ── REGEX ─────────────────────────────────────────────────────────────────

    @Test
    void regex_matches_noError() {
        EffectiveRuleDto r = rule("DE11", "STAN");
        r.setPatternRegex("\\d{6}");
        Map<Integer, String> fields = Map.of(11, "000041");

        assertThat(RulesEngine.evaluate(fields, List.of(r))).isEmpty();
    }

    @Test
    void regex_noMatch_oneError() {
        EffectiveRuleDto r = rule("DE11", "STAN");
        r.setPatternRegex("\\d{6}");
        Map<Integer, String> fields = Map.of(11, "00004A"); // contains letter

        List<ValidationErrorDTO> errors = RulesEngine.evaluate(fields, List.of(r));
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).getErrorCode()).isEqualTo("ERR-DE11-REGEX");
    }

    // ── ALLOWED_VALUES ────────────────────────────────────────────────────────

    @Test
    void allowedValues_valueAllowed_noError() {
        EffectiveRuleDto r = rule("DE3", "Processing Code");
        r.setAllowedValues(List.of(av("000000"), av("200000"), av("400000")));
        Map<Integer, String> fields = Map.of(3, "000000");

        assertThat(RulesEngine.evaluate(fields, List.of(r))).isEmpty();
    }

    @Test
    void allowedValues_valueNotAllowed_oneError() {
        EffectiveRuleDto r = rule("DE3", "Processing Code");
        r.setAllowedValues(List.of(av("000000"), av("200000")));
        Map<Integer, String> fields = Map.of(3, "999999");

        List<ValidationErrorDTO> errors = RulesEngine.evaluate(fields, List.of(r));
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).getErrorCode()).isEqualTo("ERR-DE3-ALLOWED_VALUES");
    }

    // ── EDGE CASES ────────────────────────────────────────────────────────────

    @Test
    void mandatoryFails_skipOtherChecks_onlyOneError() {
        // Mandatory fails → no MAX_LENGTH check should fire too
        EffectiveRuleDto r = rule("DE2", "PAN");
        r.setIsMandatory(true);
        r.setMaxLength(19);
        Map<Integer, String> fields = Map.of(); // DE2 absent

        List<ValidationErrorDTO> errors = RulesEngine.evaluate(fields, List.of(r));
        assertThat(errors).hasSize(1); // only MANDATORY, not MAX_LENGTH too
    }

    @Test
    void fieldAbsentAndNotMandatory_noError() {
        EffectiveRuleDto r = rule("DE48", "Additional Data");
        r.setMaxLength(999);
        Map<Integer, String> fields = Map.of(); // DE48 absent — optional

        assertThat(RulesEngine.evaluate(fields, List.of(r))).isEmpty();
    }

    @Test
    void emptyRules_alwaysPasses() {
        Map<Integer, String> fields = Map.of(2, "4111111111111111", 3, "000000");
        assertThat(RulesEngine.evaluate(fields, List.of())).isEmpty();
    }

    @Test
    void severityWarning_propagatesCorrectly() {
        EffectiveRuleDto r = rule("DE48", "Additional Data");
        r.setIsMandatory(true);
        r.setSeverity("WARNING");
        Map<Integer, String> fields = Map.of();

        List<ValidationErrorDTO> errors = RulesEngine.evaluate(fields, List.of(r));
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).getSeverity()).isEqualTo("WARNING");
    }

    @Test
    void deNumberWithPrefix_parsedCorrectly() {
        EffectiveRuleDto r = rule("DE2", "PAN");
        r.setIsMandatory(true);
        assertThat(RulesEngine.parseDeNumber("DE2")).isEqualTo(2);
        assertThat(RulesEngine.parseDeNumber("de2")).isEqualTo(2);
        assertThat(RulesEngine.parseDeNumber("2")).isEqualTo(2);
        assertThat(RulesEngine.parseDeNumber("INVALID")).isEqualTo(-1);
        assertThat(RulesEngine.parseDeNumber(null)).isEqualTo(-1);
    }
}