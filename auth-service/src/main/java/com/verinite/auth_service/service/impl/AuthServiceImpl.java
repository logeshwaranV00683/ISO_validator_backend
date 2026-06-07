package com.verinite.auth_service.service.impl;

import com.verinite.auth_service.exception.ResourceNotFoundException;
import com.verinite.auth_service.util.AuthConstants;
import com.verinite.auth_service.dto.LoginRequest;
import com.verinite.auth_service.dto.LoginResponse;
import com.verinite.auth_service.entity.User;
import com.verinite.auth_service.entity.UserSession;
import com.verinite.auth_service.repository.UserRepository;
import com.verinite.auth_service.repository.UserSessionRepository;
import com.verinite.auth_service.service.AuthService;
import com.verinite.auth_service.service.LoginAttemptService;
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
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final UserSessionRepository sessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final PrivateKey jwtPrivateKey;
    private final LoginAttemptService loginAttemptService;

    @Value("${jwt.expiry-minutes:60}")
    private int expiryMinutes;

    @Override
    @Transactional
    public LoginResponse login(
            LoginRequest request,
            String ipAddress,
            String userAgent
    ) {

        User user = userRepository
                .findByUsernameAndDeletedAtIsNull(request.getUsername())
                .orElseThrow(() ->
                        new RuntimeException("Invalid username or password"));

        if (user.getLockedUntil() != null
                && user.getLockedUntil().isAfter(LocalDateTime.now(ZoneOffset.UTC))) {
            throw new RuntimeException("Account locked until " + user.getLockedUntil());
        }

        if (!user.isActive()) {
            throw new RuntimeException("Account is disabled");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {

            // Compute the new count locally — we know recordFailedAttempt
            // increments by exactly 1 and locks when it hits MAX_FAILURES.
            // This avoids any Hibernate L1-cache / cross-transaction visibility issue.
            int newCount = user.getFailedLoginCount() + 1;

            loginAttemptService.recordFailedAttempt(user.getId());

            if (newCount >= AuthConstants.MAX_FAILURES) {
                throw new RuntimeException(
                        "Too many failed attempts. Account locked for "
                                + AuthConstants.LOCK_MINUTES
                                + " minutes."
                );
            }

            int remaining = AuthConstants.MAX_FAILURES - newCount;

            throw new RuntimeException(
                    "Invalid username or password. "
                            + remaining
                            + " attempt(s) remaining."
            );
        }

        user.setFailedLoginCount(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(LocalDateTime.now(ZoneOffset.UTC));
        user.setLastLoginIp(ipAddress);

        userRepository.save(user);

        String jti = UUID.randomUUID().toString();
        Date issuedAt = new Date();
        Date expiresAt = new Date(issuedAt.getTime() + ((long) expiryMinutes * 60 * 1000));

        String token = Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("username", user.getUsername())
                .claim("role", user.getRole().name())
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
                .username(user.getUsername())
                .role(user.getRole().name())
                .avatarInitials(user.getAvatarInitials())
                .expiresAt(expiresAt.toInstant().atZone(ZoneId.of("UTC")).toLocalDateTime())
                .build();
    }

    @Override
    public boolean validateToken(String jti) {
        return sessionRepository.findByJti(jti)
                .map(s -> s.getRevokedAt() == null
                        && s.getExpiresAt().isAfter(LocalDateTime.now()))
                .orElse(false);
    }

    @Override
    @Transactional
    public void changePassword(Long userId,String currentPassword, String newPassword) {

        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "User not found with id: " + userId));

        if (!passwordEncoder.matches(
                currentPassword, user.getPasswordHash())) {
            throw new RuntimeException("Current password is incorrect");
        }

        // Revoke all active sessions
        sessionRepository
                .findByUserIdAndRevokedAtIsNull(userId)
                .forEach(session -> {
                    session.setRevokedAt(LocalDateTime.now());
                    session.setRevokeReason("PASSWORD_CHANGE");
                    sessionRepository.save(session);
                });

        user.setPasswordHash(
                passwordEncoder.encode(newPassword));
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
        String jti = UUID.randomUUID().toString();
        Date issuedAt  = new Date();
        Date expiresAt = new Date(issuedAt.getTime() + ((long) expiryMinutes * 60 * 1000));

        String token = Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("username", user.getUsername())
                .claim("role", user.getRole().name())
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
                .username(user.getUsername())
                .role(user.getRole().name())
                .avatarInitials(user.getAvatarInitials())
                .expiresAt(expiresAt.toInstant().atZone(ZoneId.of("UTC")).toLocalDateTime())
                .build();
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}