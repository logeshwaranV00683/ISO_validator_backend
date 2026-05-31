package com.verinite.auth_service.controller;

import com.verinite.auth_service.dto.ChangePasswordRequest;
import com.verinite.auth_service.dto.LoginRequest;
import com.verinite.auth_service.dto.LoginResponse;
import com.verinite.auth_service.service.AuthService;
import com.verinite.common.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    // Public — no JWT needed
    @PostMapping("/auth/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@RequestBody @Valid LoginRequest request, HttpServletRequest httpRequest) {
        LoginResponse response = authService.login(request,httpRequest.getRemoteAddr(),httpRequest.getHeader("User-Agent"));
        return ResponseEntity.ok(ApiResponse.success(response, "Login successful"));
    }

    // Needs valid JWT — jti comes from filter
    @PostMapping("/auth/logout")
    public ResponseEntity<ApiResponse<Void>> logout(HttpServletRequest request) {
        String jti = (String) request.getAttribute("jti");
        authService.logout(jti);
        return ResponseEntity.ok(ApiResponse.success(null, "Logged out successfully"));
    }

    // Internal — Janani's Gateway calls this
    @GetMapping("/internal/auth/validate-token")
    public ResponseEntity<ApiResponse<Boolean>> validateToken(@RequestParam String jti) {
        boolean valid = authService.validateToken(jti);
        return ResponseEntity.ok(ApiResponse.success(valid, "Token validation result"));
    }

    // Change password — ADMIN or own account
    @PutMapping("/users/{id}/password")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> changePassword(@PathVariable Long id, @RequestBody @Valid ChangePasswordRequest request) {
        authService.changePassword(id, request.getCurrentPassword(), request.getNewPassword());
        return ResponseEntity.ok(ApiResponse.success(null, "Password changed successfully"));
    }
}