package com.verinite.auth_service.controller;

import com.verinite.auth_service.dto.ChangePasswordRequest;
import com.verinite.auth_service.dto.LoginRequest;
import com.verinite.auth_service.dto.LoginResponse;
import com.verinite.auth_service.dto.UserDto;
import com.verinite.auth_service.entity.User;
import com.verinite.auth_service.event.AuditEventPublisher;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.PublicKey;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final AuthService         authService;
    private final UserService         userService;
    private final UserRepository      userRepository;
    private final AuditEventPublisher auditPublisher;
    private final PublicKey           jwtPublicKey;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @RequestBody @Valid LoginRequest request,
            HttpServletRequest httpRequest) {

        String ip        = httpRequest.getRemoteAddr();
        String userAgent = httpRequest.getHeader("User-Agent");
        LoginResponse response = authService.login(request, ip, userAgent);

        auditPublisher.publish(
                "LOGIN", "USER", null, request.getUsername(),
                null, request.getUsername(), null,
                null, null, "User logged in", ip, null);

        return ResponseEntity.ok(ApiResponse.success(response, "Login successful"));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @RequestHeader("Authorization") String authHeader,
            HttpServletRequest httpRequest) {

        Claims claims   = parseClaims(authHeader);
        String jti      = claims.getId();
        String username = (String) claims.get("username");
        String ip       = httpRequest.getRemoteAddr();

        authService.logout(jti);
        auditPublisher.publish("LOGOUT", "USER", null, username,
                null, username, null, null, null, "User logged out", ip, null);

        return ResponseEntity.ok(ApiResponse.success(null, "Logged out successfully"));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<LoginResponse>> refresh(
            @RequestHeader("Authorization") String authHeader,
            HttpServletRequest httpRequest) {

        Claims claims    = parseClaims(authHeader);
        String oldJti    = claims.getId();
        Long   userId    = Long.parseLong(claims.getSubject());
        String ip        = httpRequest.getRemoteAddr();
        String userAgent = httpRequest.getHeader("User-Agent");

        authService.logout(oldJti);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        LoginResponse response = authService.refreshToken(user, ip, userAgent);
        log.info("Token refreshed for user={}", user.getUsername());
        return ResponseEntity.ok(ApiResponse.success(response, "Token refreshed"));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserDto>> me(
            @RequestHeader("Authorization") String authHeader) {

        Claims claims = parseClaims(authHeader);
        Long userId   = Long.parseLong(claims.getSubject());
        return ResponseEntity.ok(ApiResponse.success(userService.getUserById(userId), "OK"));
    }

    @PutMapping("/change-password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody @Valid ChangePasswordRequest request,
            HttpServletRequest httpRequest) {

        Claims claims = parseClaims(authHeader);
        Long   userId = Long.parseLong(claims.getSubject());
        String jti    = claims.getId();

        authService.changePassword(userId, request.getCurrentPassword(), request.getNewPassword());
        authService.logout(jti);

        auditPublisher.publish(
                "PASSWORD_CHANGE", "USER", userId, (String) claims.get("username"),
                userId, (String) claims.get("username"), null,
                null, null, "Password changed by user",
                httpRequest.getRemoteAddr(), null);

        return ResponseEntity.ok(ApiResponse.success(null, "Password changed. Please log in again."));
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private Claims parseClaims(String authHeader) {
        String token = bearerToken(authHeader);
        return Jwts.parser()
                .verifyWith(jwtPublicKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private String bearerToken(String header) {
        if (header == null || !header.startsWith("Bearer ")) {
            throw new RuntimeException("Missing or invalid Authorization header");
        }
        return header.substring(7);
    }
}