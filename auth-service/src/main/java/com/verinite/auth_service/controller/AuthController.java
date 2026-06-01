package com.verinite.auth_service.controller;

import com.verinite.auth_service.dto.ChangePasswordRequest;
import com.verinite.auth_service.dto.LoginRequest;
import com.verinite.auth_service.dto.LoginResponse;
import com.verinite.auth_service.dto.UserDto;
import com.verinite.auth_service.entity.User;
import com.verinite.auth_service.exception.ResourceNotFoundException;
import com.verinite.auth_service.repository.UserRepository;
import com.verinite.auth_service.service.AuthService;
import com.verinite.auth_service.service.UserService;
import com.verinite.common.dto.ApiResponse;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;


import java.security.PublicKey;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UserService userService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PublicKey jwtPublicKey;

    /** POST /auth/login */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @RequestBody @Valid LoginRequest request,
            HttpServletRequest httpRequest) {

        String ip        = httpRequest.getRemoteAddr();
        String userAgent = httpRequest.getHeader("User-Agent");
        LoginResponse response = authService.login(request, ip, userAgent);
        return ResponseEntity.ok(ApiResponse.success(response, "Login successful"));
    }

    /** POST /auth/logout  — requires Bearer token */
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @RequestHeader("Authorization") String authHeader) {

        String jti = extractJti(authHeader);
        authService.logout(jti);
        return ResponseEntity.ok(ApiResponse.success(null, "Logged out successfully"));
    }

    /** GET /auth/me  — returns profile of the token owner */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserDto>> me(
            @RequestHeader("Authorization") String authHeader) {

        Claims claims = parseClaims(authHeader);
        Long userId = Long.parseLong(claims.getSubject());

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        return ResponseEntity.ok(ApiResponse.success(userService.getUserById(userId), "OK"));
    }

    /** PUT /auth/change-password  — token owner changes own password */
    @PutMapping("/change-password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody @Valid ChangePasswordRequest request) {

        Claims claims = parseClaims(authHeader);
        Long userId = Long.parseLong(claims.getSubject());

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new RuntimeException("Current password is incorrect");
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        // Revoke current session so user must log in again
        String jti = extractJti(authHeader);
        authService.logout(jti);

        return ResponseEntity.ok(ApiResponse.success(null, "Password changed. Please log in again."));
    }

    // Internal — Janani's Gateway calls this
    @GetMapping("/internal/validate-token")
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
    // ── helpers ──────────────────────────────────────────────────────────────

    private Claims parseClaims(String authHeader) {
        String token = bearerToken(authHeader);
        return Jwts.parser()
                .verifyWith(jwtPublicKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private String extractJti(String authHeader) {
        return parseClaims(authHeader).getId();
    }


    private String bearerToken(String header) {
        if (header == null || !header.startsWith("Bearer ")) {
            throw new RuntimeException("Missing or invalid Authorization header");
        }
        return header.substring(7);
    }
}