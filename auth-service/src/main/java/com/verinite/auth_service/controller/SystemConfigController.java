package com.verinite.auth_service.controller;

import com.verinite.auth_service.entity.SystemConfig;
import com.verinite.auth_service.repository.SystemConfigRepository;
import com.verinite.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/config")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class SystemConfigController {

    private final SystemConfigRepository systemConfigRepository;

    @GetMapping
    public ResponseEntity<ApiResponse<List<SystemConfig>>> getAll() {
        return ResponseEntity.ok(ApiResponse.success(systemConfigRepository.findAll(),"Configs fetched"));
    }

    @GetMapping("/{key}")
    public ResponseEntity<ApiResponse<SystemConfig>> getByKey(@PathVariable String key) {
        SystemConfig config = systemConfigRepository
                .findByConfigKey(key)
                .orElseThrow(() ->
                        new RuntimeException("Config not found: " + key));
        return ResponseEntity.ok(ApiResponse.success(config, "Config fetched"));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<SystemConfig>> create(@RequestBody SystemConfig config) {
        return ResponseEntity.ok(ApiResponse.success(
                systemConfigRepository.save(config),
                "Config created"));
    }

    @PutMapping("/{key}")
    public ResponseEntity<ApiResponse<SystemConfig>> update(@PathVariable String key, @RequestBody SystemConfig updated) {
        SystemConfig config = systemConfigRepository
                .findByConfigKey(key)
                .orElseThrow(() ->
                        new RuntimeException("Config not found: " + key));
        config.setConfigValue(updated.getConfigValue());
        config.setDescription(updated.getDescription());
        return ResponseEntity.ok(ApiResponse.success(
                systemConfigRepository.save(config),
                "Config updated"));
    }

    @DeleteMapping("/{key}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable String key) {
        SystemConfig config = systemConfigRepository
                .findByConfigKey(key)
                .orElseThrow(() ->
                        new RuntimeException("Config not found: " + key));
        systemConfigRepository.delete(config);
        return ResponseEntity.ok(ApiResponse.success(null, "Config deleted"));
    }
}