package com.verinite.profile.controller;

import com.verinite.profile.dto.CreateFormatRequest;
import com.verinite.profile.dto.FormatDto;
import com.verinite.profile.dto.UpdateFormatRequest;
import com.verinite.profile.service.FormatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/formats")
@RequiredArgsConstructor
public class FormatController {

    private final FormatService formatService;

    @PostMapping
    public ResponseEntity<FormatDto> createFormat(
            @RequestBody @Valid CreateFormatRequest req) {
        return ResponseEntity.status(201).body(formatService.create(req));
    }

    @GetMapping("/{id}")
    public ResponseEntity<FormatDto> getFormat(@PathVariable Long id) {
        return ResponseEntity.ok(formatService.getById(id));
    }

    // PUT /formats/{id} — update xml, save old to versions, bump version
    @PutMapping("/{id}")
    public ResponseEntity<FormatDto> updateFormat(
            @PathVariable Long id,
            @RequestBody @Valid UpdateFormatRequest req,
            @RequestHeader(value = "X-Auth-Username", defaultValue = "system") String username) {
        return ResponseEntity.ok(formatService.update(id, req.getXmlContent(), username));
    }

    // PUT /formats/{id}/rollback — restore previous version
    @PutMapping("/{id}/rollback")
    public ResponseEntity<FormatDto> rollbackFormat(@PathVariable Long id) {
        return ResponseEntity.ok(formatService.rollback(id));
    }

    // GET /formats/{id}/versions — list all versions
    @GetMapping("/{id}/versions")
    public ResponseEntity<List<FormatDto>> getVersions(@PathVariable Long id) {
        return ResponseEntity.ok(formatService.getVersions(id));
    }
}