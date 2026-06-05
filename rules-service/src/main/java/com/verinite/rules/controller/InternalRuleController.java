package com.verinite.rules.controller;

import com.verinite.rules.entity.FieldDefinition;
import com.verinite.rules.entity.ValidationRule;
import com.verinite.rules.service.FieldDefinitionService;
import com.verinite.rules.service.RuleService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Internal Feign endpoints — NOT exposed at the API Gateway.
 * Gateway routes only /rules/** and /field-definitions/**,
 * so /internal/** is network-isolated to the cluster.
 */
@RestController
@RequiredArgsConstructor
public class InternalRuleController {

    private final RuleService             ruleService;
    private final FieldDefinitionService  fieldDefinitionService;

    /**
     * GET /internal/rules/effective?profileId=1&mti=0200
     * Called by validation-engine via Feign — returns active, date-windowed, priority-sorted rules.
     */
    @GetMapping("/internal/rules/effective")
    public List<ValidationRule> getEffectiveRules(
            @RequestParam Long   profileId,
            @RequestParam String mti) {
        return ruleService.getEffectiveRulesForEngine(profileId, mti);
    }

    /**
     * GET /internal/field-definitions?profileId=1&mti=0200
     * Called by validation-engine for message builder — returns ALL fields including hidden ones.
     */
    @GetMapping("/internal/field-definitions")
    public List<FieldDefinition> getFieldDefinitions(
            @RequestParam Long   profileId,
            @RequestParam String mti) {
        return fieldDefinitionService.getByProfileAndMti(profileId, mti);
    }
}