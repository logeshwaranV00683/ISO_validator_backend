package com.verinite.rules.controller;

import com.verinite.rules.dto.*;
import com.verinite.rules.event.RuleEventPublisher;
import com.verinite.rules.service.RuleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/rules")
@RequiredArgsConstructor
public class RuleController {

    private final RuleService        ruleService;
    private final RuleEventPublisher eventPublisher;

    // GET /rules?profileId=1&mti=0200
    @GetMapping
    public ResponseEntity<List<RuleDto>> getRules(
            @RequestParam Long   profileId,
            @RequestParam String mti) {
        return ResponseEntity.ok(ruleService.getEffectiveRules(profileId, mti));
    }

    // GET /rules/{id}
    @GetMapping("/{id}")
    public ResponseEntity<RuleDto> getRuleById(@PathVariable Long id) {
        return ResponseEntity.ok(ruleService.getRuleById(id));
    }

    // GET /rules/export?profileId=1&mti=0200
    @GetMapping("/export")
    public ResponseEntity<List<RuleDto>> exportRules(
            @RequestParam Long   profileId,
            @RequestParam String mti) {
        return ResponseEntity.ok(ruleService.exportRules(profileId, mti));
    }

    // POST /rules
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RuleDto> createRule(@RequestBody @Valid CreateRuleRequest req) {
        RuleDto created = ruleService.create(req);
        eventPublisher.publishRuleUpdated(created.getProfileId(), created.getMti());
        return ResponseEntity.status(201).body(created);
    }

    // PUT /rules/{id}
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RuleDto> updateRule(
            @PathVariable Long              id,
            @RequestBody  UpdateRuleRequest req) {
        RuleDto updated = ruleService.update(id, req);
        eventPublisher.publishRuleUpdated(updated.getProfileId(), updated.getMti());
        return ResponseEntity.ok(updated);
    }

    // PATCH /rules/{id}/status — toggle active/inactive
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RuleDto> toggleStatus(@PathVariable Long id) {
        RuleDto updated = ruleService.toggleStatus(id);
        eventPublisher.publishRuleUpdated(updated.getProfileId(), updated.getMti());
        return ResponseEntity.ok(updated);
    }

    // DELETE /rules/{id} — soft delete → 204 No Content
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteRule(@PathVariable Long id) {
        // Capture profileId + mti BEFORE soft-delete so we can publish the event
        var rule = ruleService.getById(id);
        ruleService.softDelete(id);
        eventPublisher.publishRuleDeleted(rule.getProfileId(), rule.getMti());
        return ResponseEntity.noContent().build();     // 204
    }

    // POST /rules/bulk — JSON array import in single transaction
    @PostMapping("/bulk")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BulkImportResult> bulkImport(
            @RequestBody @Valid BulkImportRulesRequest req) {
        // Cache invalidation is published inside bulkImport()
        return ResponseEntity.ok(ruleService.bulkImport(req));
    }

    // PATCH /rules/reorder — batch priority update
    @PatchMapping("/reorder")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> reorder(@RequestBody @Valid ReorderRulesRequest req) {
        ruleService.reorder(req);
        return ResponseEntity.ok().build();
    }

    // POST /rules/{id}/allowed-values
    @PostMapping("/{id}/allowed-values")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> addAllowedValue(
            @PathVariable Long                    id,
            @RequestBody  @Valid AddAllowedValueRequest req) {
        ruleService.addAllowedValue(id, req.getValue());
        return ResponseEntity.ok().build();
    }

    // DELETE /rules/{id}/allowed-values/{value}
    @DeleteMapping("/{id}/allowed-values/{value}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> removeAllowedValue(
            @PathVariable Long   id,
            @PathVariable String value) {
        ruleService.removeAllowedValue(id, value);
        return ResponseEntity.noContent().build();     // 204
    }
}