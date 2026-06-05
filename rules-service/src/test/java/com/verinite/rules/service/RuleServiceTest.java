package com.verinite.rules.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.verinite.common.enums.DataType;
import com.verinite.common.enums.Severity;
import com.verinite.rules.dto.CreateRuleRequest;
import com.verinite.rules.dto.RuleDto;
import com.verinite.rules.entity.ValidationRule;
import com.verinite.rules.event.RuleEventPublisher;
import com.verinite.rules.repository.RuleAllowedValueRepository;
import com.verinite.rules.repository.RuleRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RuleServiceTest {

    @Mock private RuleRepository             ruleRepository;
    @Mock private RuleAllowedValueRepository allowedValueRepository;
    @Mock private RuleEventPublisher         eventPublisher;
    @Mock private ObjectMapper               objectMapper;   // used internally by publishAudit
    @InjectMocks private RuleService         ruleService;

    private ValidationRule rule;
    private CreateRuleRequest req;

    @BeforeEach
    void setUp() throws Exception {
        rule = ValidationRule.builder()
                .id(1L).profileId(1L).profileName("Visa Switch")
                .mti("0200").deNumber("DE7")
                .fieldName("Transmission Date & Time")
                .isMandatory(true).dataType(DataType.valueOf("numeric"))
                .severity(Severity.valueOf("CRITICAL")).priority(1)
                .active(true).allowedValues(new ArrayList<>())
                .build();

        req = new CreateRuleRequest();
        req.setProfileId(1L);
        req.setProfileName("Visa Switch");
        req.setMti("0200");
        req.setDeNumber("DE7");
        req.setFieldName("Transmission Date & Time");
        req.setDataType(DataType.valueOf("numeric"));
        req.setSeverity(Severity.valueOf("CRITICAL"));
        req.setIsMandatory(true);
        req.setAllowedValues(List.of());

        // objectMapper is called inside toJson() → just return "{}" for all invocations
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
    }

    // ── create ─────────────────────────────────────────────────

    @Test
    void create_success_returnsDtoWithCorrectFields() {
        when(ruleRepository
                .existsByProfileIdAndMtiAndDeNumberAndDeletedAtIsNull(1L, "0200", "DE7"))
                .thenReturn(false);
        when(ruleRepository.save(any(ValidationRule.class))).thenReturn(rule);

        RuleDto result = ruleService.create(req);

        assertThat(result.getProfileId()).isEqualTo(1L);
        assertThat(result.getMti()).isEqualTo("0200");
        assertThat(result.getDeNumber()).isEqualTo("DE7");
        verify(ruleRepository).save(any(ValidationRule.class));
    }

    @Test
    void create_duplicateRule_throwsIllegalStateException() {
        when(ruleRepository
                .existsByProfileIdAndMtiAndDeNumberAndDeletedAtIsNull(1L, "0200", "DE7"))
                .thenReturn(true);

        assertThatThrownBy(() -> ruleService.create(req))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Rule already exists");

        verify(ruleRepository, never()).save(any());
    }

    // ── getRuleById ────────────────────────────────────────────

    @Test
    void getRuleById_found_returnsDto() {
        when(ruleRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(rule));

        RuleDto result = ruleService.getRuleById(1L);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getDeNumber()).isEqualTo("DE7");
    }

    @Test
    void getRuleById_notFound_throwsEntityNotFoundException() {
        when(ruleRepository.findByIdAndDeletedAtIsNull(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ruleService.getRuleById(99L))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("99");
    }

    // ── softDelete ─────────────────────────────────────────────

    @Test
    void softDelete_success_setsDeletedAtAndDeactivates() {
        when(ruleRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(rule));
        when(ruleRepository.save(any())).thenReturn(rule);

        ruleService.softDelete(1L);

        assertThat(rule.getDeletedAt()).isNotNull();
        assertThat(rule.getActive()).isFalse();
        verify(ruleRepository).save(rule);
    }

    @Test
    void softDelete_notFound_throwsEntityNotFoundException() {
        when(ruleRepository.findByIdAndDeletedAtIsNull(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ruleService.softDelete(99L))
                .isInstanceOf(EntityNotFoundException.class);
    }
}