package com.verinite.rules.controller;

import com.verinite.rules.entity.FieldDefinition;
import com.verinite.rules.entity.ValidationRule;
import com.verinite.rules.service.RuleService;
import com.verinite.rules.service.FieldDefinitionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/internal/rules")
@RequiredArgsConstructor
public class InternalRuleController {

    private final RuleService ruleService;
    private final FieldDefinitionService fieldDefinitionService;

    // Called by Validation Engine via Feign — returns raw entity list
    @GetMapping
    public List<ValidationRule> getRulesInternal(
            @RequestParam Long profileId,
            @RequestParam String mti) {
        return ruleService.getEffectiveRulesForEngine(profileId, mti);
    }
    @GetMapping("/field-definitions")
    public List<FieldDefinition> getFieldDefinitions(
            @RequestParam Long profileId,
            @RequestParam String mti) {
        // Returns ALL fields including hidden ones — engine needs them all
        return fieldDefinitionService.getByProfileAndMti(profileId, mti);
    }
}