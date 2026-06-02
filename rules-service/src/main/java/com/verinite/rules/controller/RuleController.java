package com.verinite.rules.controller;

import com.verinite.rules.dto.*;
import com.verinite.rules.entity.ValidationRule;
import com.verinite.rules.event.RuleEventPublisher;
import com.verinite.rules.service.RuleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/rules")
@RequiredArgsConstructor
public class RuleController {

    private final RuleService ruleService;
    private final RuleEventPublisher ruleEventPublisher;

    // POST /rules
    @PostMapping
    public ResponseEntity<RuleDto> createRule(@RequestBody @Valid CreateRuleRequest req) {
        RuleDto created = ruleService.create(req);
        ruleEventPublisher.publishRuleUpdated(req.getProfileId(), req.getMti());
        return ResponseEntity.status(201).body(created);
    }

    // GET /rules?profileId=1&mti=0200
    @GetMapping
    public ResponseEntity<List<RuleDto>> getRules(
            @RequestParam Long profileId,
            @RequestParam String mti) {
        return ResponseEntity.ok(ruleService.getEffectiveRules(profileId, mti));
    }

    // PUT /rules/{id}
    @PutMapping("/{id}")
    public ResponseEntity<RuleDto> updateRule(
            @PathVariable Long id,
            @RequestBody UpdateRuleRequest req) {
        RuleDto updated = ruleService.update(id, req);
        ruleEventPublisher.publishRuleUpdated(updated.getProfileId(), updated.getMti());
        return ResponseEntity.ok(updated);
    }

    // DELETE /rules/{id}  — SOFT DELETE only
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRule(@PathVariable Long id) {
        ValidationRule rule = ruleService.getById(id);  // grab before deleting for event
        ruleService.softDelete(id);
        ruleEventPublisher.publishRuleDeleted(rule.getProfileId(), rule.getMti());
        return ResponseEntity.ok().build();
    }

    // POST /rules/{id}/allowed-values
    @PostMapping("/{id}/allowed-values")
    public ResponseEntity<Void> addAllowedValue(
            @PathVariable Long id,
            @RequestBody @Valid AddAllowedValueRequest req) {
        ruleService.addAllowedValue(id, req.getValue());
        return ResponseEntity.ok().build();
    }

    // DELETE /rules/{id}/allowed-values/{value}
    @DeleteMapping("/{id}/allowed-values/{value}")
    public ResponseEntity<Void> removeAllowedValue(
            @PathVariable Long id,
            @PathVariable String value) {
        ruleService.removeAllowedValue(id, value);
        return ResponseEntity.ok().build();
    }
}