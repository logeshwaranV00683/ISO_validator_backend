package com.verinite.auth_service.controller;

import com.verinite.auth_service.dto.CreateUserRequest;
import com.verinite.auth_service.dto.SessionDto;
import com.verinite.auth_service.dto.UpdateUserRequest;
import com.verinite.auth_service.dto.UserDto;
import com.verinite.auth_service.entity.UserSession;
import com.verinite.auth_service.repository.UserSessionRepository;
import com.verinite.auth_service.service.UserService;
import com.verinite.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
@Slf4j
public class UserController {

    private final UserService            userService;
    private final UserSessionRepository  sessionRepository;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<UserDto>> createUser(
            @RequestBody @Valid CreateUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(userService.createUser(request), "User created"));
    }

    // GAP 5 FIX: paginated. BUG 1/2/3 FIX: role param now accepted and passed through
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','ANALYST','VIEWER')")
    public ResponseEntity<ApiResponse<Page<UserDto>>> getUsers(
            @RequestParam(required = false)          String role,
            @RequestParam(defaultValue = "0")        int    page,
            @RequestParam(defaultValue = "20")       int    size,
            @RequestParam(defaultValue = "username") String sortBy,
            @RequestParam(defaultValue = "asc")      String sortDir) {

        Sort sort = sortDir.equalsIgnoreCase("asc")
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();
        Page<UserDto> result = userService.getAllUsers(role, PageRequest.of(page, size, sort));
        return ResponseEntity.ok(ApiResponse.success(result, "Users fetched"));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','ANALYST','VIEWER')")
    public ResponseEntity<ApiResponse<UserDto>> getUserById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(userService.getUserById(id), "User fetched"));
    }

    // BUG 2 FIX: UpdateUserRequest — no password field
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<UserDto>> updateUser(
            @PathVariable Long id,
            @RequestBody @Valid UpdateUserRequest request) {
        return ResponseEntity.ok(ApiResponse.success(userService.updateUser(id, request), "User updated"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return ResponseEntity.ok(ApiResponse.success(null, "User deleted"));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<UserDto>> toggleStatus(
            @PathVariable Long id, @RequestParam boolean active) {
        return ResponseEntity.ok(ApiResponse.success(
                userService.setActive(id, active),
                active ? "User activated" : "User deactivated"));
    }

    @PatchMapping("/{id}/role")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<UserDto>> changeRole(
            @PathVariable Long id, @RequestParam String role) {
        return ResponseEntity.ok(ApiResponse.success(userService.changeRole(id, role), "Role updated"));
    }

    @PostMapping("/{id}/reset-password")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @PathVariable Long id, @RequestParam String newPassword) {
        userService.resetPassword(id, newPassword);
        return ResponseEntity.ok(ApiResponse.success(null, "Password reset"));
    }

    @GetMapping("/{id}/sessions")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<SessionDto>>> listSessions(@PathVariable Long id) {
        List<SessionDto> sessions = sessionRepository
                .findByUserIdOrderByIssuedAtDesc(id)
                .stream()
                .map(this::toSessionDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success(sessions, "Sessions fetched"));
    }

    @DeleteMapping("/{id}/sessions")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> revokeAllSessions(@PathVariable Long id) {
        List<UserSession> active = sessionRepository.findByUserIdAndRevokedAtIsNull(id);
        active.forEach(s -> {
            s.setRevokedAt(LocalDateTime.now(ZoneOffset.UTC));
            s.setRevokeReason("ADMIN_REVOKE");
            s.setIsActive(false);
            sessionRepository.save(s);
        });
        log.info("Revoked {} sessions for userId={}", active.size(), id);
        return ResponseEntity.ok(ApiResponse.success(null,
                "Revoked " + active.size() + " session(s)"));
    }

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