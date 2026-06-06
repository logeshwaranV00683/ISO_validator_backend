package com.verinite.ai.controller;

import com.verinite.ai.entity.AiPromptTemplate;
import com.verinite.ai.service.AiTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/ai/templates")
@RequiredArgsConstructor
public class AiTemplateController {

    private final AiTemplateService templateService;

    @PostMapping
    public ResponseEntity<AiPromptTemplate> create(
            @RequestBody AiPromptTemplate template,
            @RequestHeader(value = "X-Username", defaultValue = "system") String username) {
        return ResponseEntity.ok(templateService.create(template, username));
    }

    @GetMapping
    public ResponseEntity<List<AiPromptTemplate>> getAll() {
        return ResponseEntity.ok(templateService.getAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<AiPromptTemplate> getById(@PathVariable Long id) {
        return ResponseEntity.ok(templateService.getById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AiPromptTemplate> update(
            @PathVariable Long id,
            @RequestBody AiPromptTemplate template,
            @RequestHeader(value = "X-Username", defaultValue = "system") String username) {
        return ResponseEntity.ok(templateService.update(id, template, username));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            @RequestHeader(value = "X-Username", defaultValue = "system") String username) {
        templateService.softDelete(id, username);
        return ResponseEntity.noContent().build();
    }
}