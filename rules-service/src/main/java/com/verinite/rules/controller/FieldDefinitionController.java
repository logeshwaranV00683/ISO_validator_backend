package com.verinite.rules.controller;

import com.verinite.rules.dto.*;
import com.verinite.rules.service.FieldDefinitionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/field-definitions")
@RequiredArgsConstructor
public class FieldDefinitionController {

    private final FieldDefinitionService fieldDefinitionService;

    // GET /field-definitions?profileId=1&mti=0200
    // Returns only is_builder_visible=true fields
    @GetMapping
    public ResponseEntity<List<FieldDefinitionDto>> getFieldDefinitions(
            @RequestParam Long profileId,
            @RequestParam String mti) {

        return ResponseEntity.ok(
                fieldDefinitionService.getByProfileAndMti(profileId, mti)
                        .stream()
                        .map(fieldDefinitionService::toDto)
                        .toList()
        );

    }

    // GET /field-definitions/{id}
    @GetMapping("/{id}")
    public ResponseEntity<FieldDefinitionDto> getById(@PathVariable Long id) {
        return ResponseEntity.ok(fieldDefinitionService.getById(id));
    }

    // POST /field-definitions
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<FieldDefinitionDto> create(
            @RequestBody @Valid CreateFieldDefinitionRequest req) {
        return ResponseEntity.status(201).body(fieldDefinitionService.create(req));
    }

    // PUT /field-definitions/{id}
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<FieldDefinitionDto> update(
            @PathVariable Long                        id,
            @RequestBody  UpdateFieldDefinitionRequest req) {
        return ResponseEntity.ok(fieldDefinitionService.update(id, req));
    }

    // DELETE /field-definitions/by-format?profileId=1&mti=0200
    @DeleteMapping("/by-format")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Integer> deleteAllForFormat(
            @RequestParam Long profileId, @RequestParam String mti) {
        return ResponseEntity.ok(fieldDefinitionService.deleteAllForFormat(profileId, mti));
    }

    // DELETE /field-definitions/{id} — soft delete → 204 No Content
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        fieldDefinitionService.softDelete(id);
        return ResponseEntity.noContent().build();      // 204
    }

    // POST /field-definitions/bulk-import
    @PostMapping("/bulk-import")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BulkImportResult> bulkImport(
            @RequestBody @Valid BulkImportFieldDefinitionsRequest req) {
        return ResponseEntity.ok(fieldDefinitionService.bulkImport(req));
    }
}