package com.verinite.auth_service.controller;

import com.verinite.auth_service.service.AuthService;
import com.verinite.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Internal endpoint — NOT exposed through API Gateway.
 * Path: GET /internal/auth/validate-token?jti=<uuid>
 *
 * BUG 3 FIX: Previously this was inside AuthController with
 * @RequestMapping("/auth"), making the path /auth/internal/auth/validate-token.
 * Now it lives in its own controller with the correct path.
 */
@RestController
@RequestMapping("/internal/auth")
@RequiredArgsConstructor
public class InternalAuthController {

    private final AuthService authService;

    /**
     * GET /internal/auth/validate-token?jti={jti}
     * Called by the API Gateway (optional) for session revocation check.
     * Returns true if the session is still valid (not revoked, not expired).
     */
    @GetMapping("/validate-token")
    public ResponseEntity<ApiResponse<Boolean>> validateToken(@RequestParam String jti) {
        return ResponseEntity.ok(ApiResponse.success(authService.validateToken(jti), "OK"));
    }
}