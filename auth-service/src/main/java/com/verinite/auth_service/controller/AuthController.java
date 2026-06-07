package com.verinite.auth_service.controller;

import com.verinite.auth_service.dto.ChangePasswordRequest;
import com.verinite.auth_service.dto.LoginRequest;
import com.verinite.auth_service.dto.LoginResponse;
import com.verinite.auth_service.dto.SessionDto;
import com.verinite.auth_service.dto.UserDto;
import com.verinite.auth_service.entity.User;
import com.verinite.auth_service.entity.UserSession;
import com.verinite.auth_service.event.AuditEventPublisher;
import com.verinite.auth_service.exception.ResourceNotFoundException;
import com.verinite.auth_service.repository.UserRepository;
import com.verinite.auth_service.repository.UserSessionRepository;
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
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final AuthService            authService;
    private final UserService            userService;
    private final UserRepository         userRepository;
    private final UserSessionRepository  sessionRepository;
    private final AuditEventPublisher    auditPublisher;
    private final PublicKey              jwtPublicKey;

    /** POST /auth/login */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @RequestBody @Valid LoginRequest request,
            HttpServletRequest httpRequest) {

        String ip        = httpRequest.getRemoteAddr();
        String userAgent = httpRequest.getHeader("User-Agent");
        LoginResponse response = authService.login(request, ip, userAgent);

        // Audit: login success
        auditPublisher.publish(
                "LOGIN", "USER", null, request.getUsername(),
                null, request.getUsername(), null,
                null, null, "User logged in", ip, null);

        return ResponseEntity.ok(ApiResponse.success(response, "Login successful"));
    }

    /** POST /auth/logout — requires Bearer token */
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @RequestHeader("Authorization") String authHeader,
            HttpServletRequest httpRequest) {

        Claims claims = parseClaims(authHeader);
        String jti      = claims.getId();
        String username = (String) claims.get("username");
        String ip       = httpRequest.getRemoteAddr();

        authService.logout(jti);

        auditPublisher.publish(
                "LOGOUT", "USER", null, username,
                null, username, null,
                null, null, "User logged out", ip, null);

        return ResponseEntity.ok(ApiResponse.success(null, "Logged out successfully"));
    }

    /** POST /auth/refresh — revoke old session, issue new JWT */
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<LoginResponse>> refresh(
            @RequestHeader("Authorization") String authHeader,
            HttpServletRequest httpRequest) {

        Claims claims   = parseClaims(authHeader);
        String oldJti   = claims.getId();
        Long   userId   = Long.parseLong(claims.getSubject());
        String ip       = httpRequest.getRemoteAddr();
        String userAgent= httpRequest.getHeader("User-Agent");

        // Revoke old session
        authService.logout(oldJti);

        // Issue new token via login-like flow (reuse existing user)
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        LoginResponse response = authService.refreshToken(user, ip, userAgent);

        log.info("Token refreshed for user={}", user.getUsername());
        return ResponseEntity.ok(ApiResponse.success(response, "Token refreshed"));
    }

    /** GET /auth/me — profile of token owner */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserDto>> me(
            @RequestHeader("Authorization") String authHeader) {

        Claims claims = parseClaims(authHeader);
        Long userId = Long.parseLong(claims.getSubject());
        return ResponseEntity.ok(ApiResponse.success(userService.getUserById(userId), "OK"));
    }

    /** PUT /auth/change-password — token owner changes own password */
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

    /** GET /internal/auth/validate-token — called by gateway for revocation check */
    @GetMapping("/internal/validate-token")
    public ResponseEntity<ApiResponse<Boolean>> validateToken(@RequestParam String jti) {
        return ResponseEntity.ok(ApiResponse.success(authService.validateToken(jti), "OK"));
    }

    /** PUT /auth/users/{id}/password — ADMIN reset password */
    @PutMapping("/users/{id}/password")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @PathVariable Long id,
            @RequestBody @Valid ChangePasswordRequest request) {
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

    private String bearerToken(String header) {
        if (header == null || !header.startsWith("Bearer ")) {
            throw new RuntimeException("Missing or invalid Authorization header");
        }
        return header.substring(7);
    }

    // ── Session mapping helper ────────────────────────────────────────────

    private SessionDto toSessionDto(UserSession s) {
        return SessionDto.builder()
                .id(s.getId())
                .jti(s.getJti())
                .ipAddress(s.getIpAddress())
                .userAgent(s.getUserAgent() != null
                        ? s.getUserAgent().substring(0, Math.min(80, s.getUserAgent().length()))
                        : null)
                .issuedAt(s.getIssuedAt())
                .expiresAt(s.getExpiresAt())
                .revokedAt(s.getRevokedAt())
                .revokeReason(s.getRevokeReason())
                .isActive(s.getIsActive())
                .build();
    }
}