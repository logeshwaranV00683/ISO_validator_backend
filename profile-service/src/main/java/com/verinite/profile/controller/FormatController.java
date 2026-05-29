package com.verinite.profile.controller;

import com.verinite.profile.dto.CreateFormatRequest;
import com.verinite.profile.dto.FormatDto;
import com.verinite.profile.service.FormatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
}