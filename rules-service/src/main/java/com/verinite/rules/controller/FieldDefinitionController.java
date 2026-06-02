package com.verinite.rules.controller;

import com.verinite.rules.entity.FieldDefinition;
import com.verinite.rules.service.FieldDefinitionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/field-definitions")
@RequiredArgsConstructor
public class FieldDefinitionController {

    private final FieldDefinitionService fieldDefinitionService;

    // GET /field-definitions?profileId=1&mti=0200
    // Only returns fields where is_builder_visible = true
    // DE1 (Bitmap) with is_builder_visible=false will NOT appear here
    @GetMapping
    public ResponseEntity<List<FieldDefinition>> getFieldDefinitions(
            @RequestParam Long profileId,
            @RequestParam String mti) {
        List<FieldDefinition> fields = fieldDefinitionService.getVisibleFields(profileId, mti);
        return ResponseEntity.ok(fields);
    }
}