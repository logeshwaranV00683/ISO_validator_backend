package com.verinite.auth_service.service.impl;

import com.verinite.auth_service.dto.LoginRequest;
import com.verinite.auth_service.dto.LoginResponse;
import com.verinite.auth_service.entity.User;
import com.verinite.auth_service.entity.UserSession;
import com.verinite.auth_service.exception.ResourceNotFoundException;
import com.verinite.auth_service.repository.UserRepository;
import com.verinite.auth_service.repository.UserSessionRepository;
import com.verinite.auth_service.service.AuthService;
import com.verinite.auth_service.service.LoginAttemptService;
import com.verinite.auth_service.service.SystemConfigService;
import com.verinite.auth_service.util.AuthConstants;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {

    private final UserRepository      userRepository;
    private final UserSessionRepository sessionRepository;
    private final PasswordEncoder     passwordEncoder;
    private final PrivateKey          jwtPrivateKey;
    private final LoginAttemptService loginAttemptService;
    private final SystemConfigService systemConfigService;

//    @Value("${jwt.expiry-minutes}")
//    private int expiryMinutes;

    private static final int DEFAULT_EXPIRY_MINUTES = 60;

    @Override
    @Transactional
    public LoginResponse login(LoginRequest request, String ipAddress, String userAgent) {

        User user = userRepository
                .findByUsernameAndDeletedAtIsNull(request.getUsername())
                .orElseThrow(() -> new RuntimeException("Invalid username or password"));

        if (user.getLockedUntil() != null
                && user.getLockedUntil().isAfter(LocalDateTime.now(ZoneOffset.UTC))) {
            long secondsLeft = java.time.Duration.between(
                    LocalDateTime.now(ZoneOffset.UTC), user.getLockedUntil()).getSeconds();
            long minutesLeft = Math.max(1, (secondsLeft + 59) / 60); // round up, minimum 1
            throw new RuntimeException(
                    "Account locked. Try again in " + minutesLeft +
                            (minutesLeft == 1 ? " minute." : " minutes."));
        }

        if (!user.isActive()) {
            throw new RuntimeException("Account is disabled");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            int maxFailures = systemConfigService.getInt("login.max.failures",   AuthConstants.MAX_FAILURES);
            int lockMinutes = systemConfigService.getInt("account.lock.minutes", AuthConstants.LOCK_MINUTES);
            int newCount    = user.getFailedLoginCount() + 1;

            loginAttemptService.recordFailedAttempt(user.getId());

            if (newCount >= maxFailures) {
                throw new RuntimeException(
                        "Too many failed attempts. Account locked for " + lockMinutes + " minutes.");
            }

            throw new RuntimeException(
                    "Invalid username or password. " + (maxFailures - newCount) + " attempt(s) remaining.");
        }

        user.setFailedLoginCount(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(LocalDateTime.now(ZoneOffset.UTC));
        user.setLastLoginIp(ipAddress);
        userRepository.save(user);

        enforceSessionLimit(user.getId());

        String jti       = UUID.randomUUID().toString();
        Date   issuedAt  = new Date();
//        Date   expiresAt = new Date(issuedAt.getTime() + ((long) expiryMinutes * 60 * 1000));
        int expiryMinutes = systemConfigService.getInt("jwt.expiry.minutes", DEFAULT_EXPIRY_MINUTES);
        Date   expiresAt = new Date(issuedAt.getTime() + ((long) expiryMinutes * 60 * 1000));

        String token = Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("username", user.getUsername())
                .claim("role", user.getRole().name())
                .issuer(systemConfigService.getString("jwt.issuer", "iso8583-validator"))
                .id(jti)

                .issuedAt(issuedAt)
                .expiration(expiresAt)
                .signWith(jwtPrivateKey)
                .compact();

        UserSession session = UserSession.builder()
                .user(user)
                .jti(jti)
                .jwtTokenHash(sha256(token))
                .issuedAt(issuedAt.toInstant().atZone(ZoneId.of("UTC")).toLocalDateTime())
                .expiresAt(expiresAt.toInstant().atZone(ZoneId.of("UTC")).toLocalDateTime())
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .build();
        sessionRepository.save(session);

        log.info("Login success: user={} jti={}", user.getUsername(), jti);

        return LoginResponse.builder()
                .token(token)
                .expiresAt(expiresAt.toInstant().atZone(ZoneId.of("UTC")).toLocalDateTime())
                .user(LoginResponse.UserInfo.builder()
                        .userId(user.getId())
                        .username(user.getUsername())
                        .fullName(user.getFullName())
                        .email(user.getEmail())
                        .avatarInitials(user.getAvatarInitials())
                        .role(user.getRole().name())
                        .build())
                .build();
    }

    @Override
    public boolean validateToken(String jti) {
        return sessionRepository.findByJti(jti)
                .map(s -> s.getRevokedAt() == null
                        && s.getExpiresAt().isAfter(LocalDateTime.now(ZoneOffset.UTC)))
                .orElse(false);
    }

    @Override
    @Transactional
    public void changePassword(Long userId, String currentPassword, String newPassword) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new RuntimeException("Current password is incorrect");
        }

        sessionRepository.findByUserIdAndRevokedAtIsNull(userId).forEach(session -> {
            session.setRevokedAt(LocalDateTime.now(ZoneOffset.UTC));
            session.setRevokeReason("PASSWORD_CHANGE");
            sessionRepository.save(session);
        });

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setPasswordChangedAt(LocalDateTime.now());
        userRepository.save(user);
    }

    @Override
    @Transactional
    public void logout(String jti) {

        UserSession session = sessionRepository.findByJti(jti)
                .orElseThrow(() -> new RuntimeException("Session not found"));

        if (session.getRevokedAt() != null) {
            throw new RuntimeException("Session already revoked");
        }

        session.setRevokedAt(LocalDateTime.now(ZoneOffset.UTC));
        session.setRevokeReason("LOGOUT");
        session.setIsActive(false);
        sessionRepository.save(session);

        log.info("Logout success: jti={}", jti);
    }

    @Override
    @Transactional
    public LoginResponse refreshToken(User user, String ipAddress, String userAgent) {
        String jti       = UUID.randomUUID().toString();
        Date   issuedAt  = new Date();
//        Date   expiresAt = new Date(issuedAt.getTime() + ((long) expiryMinutes * 60 * 1000));
        int expiryMinutes = systemConfigService.getInt("jwt.expiry.minutes", DEFAULT_EXPIRY_MINUTES);
        Date   expiresAt = new Date(issuedAt.getTime() + ((long) expiryMinutes * 60 * 1000));
        String token = Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("username", user.getUsername())
                .claim("role", user.getRole().name())
                .issuer(systemConfigService.getString("jwt.issuer", "iso8583-validator"))
                .id(jti)
                .issuedAt(issuedAt)
                .expiration(expiresAt)
                .signWith(jwtPrivateKey)
                .compact();

        UserSession session = UserSession.builder()
                .user(user)
                .jti(jti)
                .jwtTokenHash(sha256(token))
                .issuedAt(issuedAt.toInstant().atZone(ZoneId.of("UTC")).toLocalDateTime())
                .expiresAt(expiresAt.toInstant().atZone(ZoneId.of("UTC")).toLocalDateTime())
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .build();
        sessionRepository.save(session);
        log.info("Token refreshed: user={} newJti={}", user.getUsername(), jti);

        return LoginResponse.builder()
                .token(token)
                .expiresAt(expiresAt.toInstant().atZone(ZoneId.of("UTC")).toLocalDateTime())
                .user(LoginResponse.UserInfo.builder()
                        .userId(user.getId())
                        .username(user.getUsername())
                        .fullName(user.getFullName())
                        .email(user.getEmail())
                        .avatarInitials(user.getAvatarInitials())
                        .role(user.getRole().name())
                        .build())
                .build();
    }

    private void enforceSessionLimit(Long userId) {
        int maxSessions = systemConfigService.getInt("login.session.max", AuthConstants.MAX_SESSIONS);

        List<UserSession> activeSessions =
                sessionRepository.findByUserIdAndRevokedAtIsNullOrderByIssuedAtAsc(userId);

        // If already at/above the cap, evict the oldest sessions until there's room for the new one
        int toEvict = activeSessions.size() - maxSessions + 1;
        for (int i = 0; i < toEvict && i < activeSessions.size(); i++) {
            UserSession oldest = activeSessions.get(i);
            oldest.setRevokedAt(LocalDateTime.now(ZoneOffset.UTC));
            oldest.setRevokeReason("SESSION_LIMIT_EXCEEDED");
            oldest.setIsActive(false);
            sessionRepository.save(oldest);
            log.info("Session evicted (limit={}): userId={} jti={}", maxSessions, userId, oldest.getJti());
        }
    }

    private String sha256(String input) {

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}