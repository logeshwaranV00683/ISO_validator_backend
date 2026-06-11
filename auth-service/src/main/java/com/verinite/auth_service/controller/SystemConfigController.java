package com.verinite.auth_service.controller;

import com.verinite.auth_service.entity.SystemConfig;
import com.verinite.auth_service.event.AuditEventPublisher;
import com.verinite.auth_service.repository.SystemConfigRepository;
import com.verinite.common.dto.ApiResponse;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.PublicKey;
import java.util.List;

@RestController
@RequestMapping("/config")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class SystemConfigController {

    private final SystemConfigRepository systemConfigRepository;
    private final AuditEventPublisher auditPublisher;
    private final PublicKey jwtPublicKey;

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

//    @PostMapping
//    public ResponseEntity<ApiResponse<SystemConfig>> create(@RequestBody SystemConfig config) {
//        return ResponseEntity.ok(ApiResponse.success(
//                systemConfigRepository.save(config),
//                "Config created"));
//    }

    @PutMapping("/{key}")
    public ResponseEntity<ApiResponse<SystemConfig>> update(
            @PathVariable String key,
            @RequestBody SystemConfig updated,
            @RequestHeader("Authorization") String authHeader,
            HttpServletRequest httpRequest) {

        Claims claims = parseClaims(authHeader);
        String username = (String) claims.get("username");
        Long   userId   = Long.parseLong(claims.getSubject());
        String ip       = httpRequest.getRemoteAddr();

        SystemConfig config = systemConfigRepository
                .findByConfigKey(key)
                .orElseThrow(() ->
                        new RuntimeException("Config not found: " + key));

        String oldValue = config.getConfigValue(); // capture before update

        config.setConfigValue(updated.getConfigValue());
        config.setDescription(updated.getDescription());
        SystemConfig saved = systemConfigRepository.save(config);

        auditPublisher.publish(
                "UPDATE", "SYSTEM_CONFIG", null, key,
                userId, username, null,
                oldValue, updated.getConfigValue(), "System config updated",
                ip, null);

        return ResponseEntity.ok(ApiResponse.success(saved, "Config updated"));
    }

//    @DeleteMapping("/{key}")
//    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable String key) {
//        SystemConfig config = systemConfigRepository
//                .findByConfigKey(key)
//                .orElseThrow(() ->
//                        new RuntimeException("Config not found: " + key));
//        systemConfigRepository.delete(config);
//        return ResponseEntity.ok(ApiResponse.success(null, "Config deleted"));
//    }

    private Claims parseClaims(String authHeader) {
        String token = authHeader.startsWith("Bearer ")
                ? authHeader.substring(7)
                : authHeader;
        return Jwts.parser()
                .verifyWith(jwtPublicKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

}