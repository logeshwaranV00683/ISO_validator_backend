package com.verinite.rules.service;

import com.verinite.rules.dto.*;
import com.verinite.rules.entity.*;
import com.verinite.rules.repository.*;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RuleService {

    private final RuleRepository ruleRepository;
    private final RuleAllowedValueRepository allowedValueRepository;

    // ── CREATE ──────────────────────────────────────────────────────────────
    public RuleDto create(CreateRuleRequest req) {
        ValidationRule rule = ValidationRule.builder()
                .profileId(req.getProfileId())
                .mti(req.getMti())
                .fieldId(req.getFieldId())
                .ruleType(req.getRuleType())
                .maxLength(req.getMaxLength())
                .minLength(req.getMinLength())
                .regexPattern(req.getRegexPattern())
                .active(true)
                .build();

        return toDto(ruleRepository.save(rule));
    }

    // ── READ (active + not deleted) ─────────────────────────────────────────
    public List<RuleDto> getEffectiveRules(Long profileId, String mti) {
        return ruleRepository.findEffectiveRules(profileId, mti)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    // ── READ (raw entity for Validation Engine internal use) ─────────────────
    public List<ValidationRule> getEffectiveRulesForEngine(Long profileId, String mti) {
        return ruleRepository.findEffectiveRules(profileId, mti);
    }

    // ── GET BY ID ──────────────────────────────────────────────────────────
    public ValidationRule getById(Long id) {
        return ruleRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Rule not found: " + id));
    }

    // ── UPDATE ─────────────────────────────────────────────────────────────
    @Transactional
    public RuleDto update(Long id, UpdateRuleRequest req) {
        ValidationRule rule = getById(id);

        if (req.getRuleType() != null)   rule.setRuleType(req.getRuleType());
        if (req.getMaxLength() != null)  rule.setMaxLength(req.getMaxLength());
        if (req.getMinLength() != null)  rule.setMinLength(req.getMinLength());
        if (req.getRegexPattern() != null) rule.setRegexPattern(req.getRegexPattern());
        if (req.getActive() != null)     rule.setActive(req.getActive());

        return toDto(ruleRepository.save(rule));
    }

    // ── SOFT DELETE ────────────────────────────────────────────────────────
    @Transactional
    public void softDelete(Long id) {
        ValidationRule rule = getById(id);
        rule.setDeletedAt(LocalDateTime.now());
        rule.setActive(false);
        ruleRepository.save(rule);
    }

    // ── ALLOWED VALUES ─────────────────────────────────────────────────────
    @Transactional
    public void addAllowedValue(Long ruleId, String value) {
        ValidationRule rule = getById(ruleId);
        RuleAllowedValue av = RuleAllowedValue.builder()
                .rule(rule)
                .value(value)
                .build();
        allowedValueRepository.save(av);
    }

    @Transactional
    public void removeAllowedValue(Long ruleId, String value) {
        allowedValueRepository.deleteByRuleIdAndValue(ruleId, value);
    }

    // ── MAPPER ─────────────────────────────────────────────────────────────
    private RuleDto toDto(ValidationRule rule) {
        List<String> values = rule.getAllowedValues() == null ? List.of() :
                rule.getAllowedValues().stream()
                        .map(RuleAllowedValue::getValue)
                        .collect(Collectors.toList());

        return RuleDto.builder()
                .id(rule.getId())
                .profileId(rule.getProfileId())
                .mti(rule.getMti())
                .fieldId(rule.getFieldId())
                .ruleType(rule.getRuleType())
                .maxLength(rule.getMaxLength())
                .minLength(rule.getMinLength())
                .regexPattern(rule.getRegexPattern())
                .active(rule.getActive())
                .createdAt(rule.getCreatedAt())
                .allowedValues(values)
                .build();
    }
}