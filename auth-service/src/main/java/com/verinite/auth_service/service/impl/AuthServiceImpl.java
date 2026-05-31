package com.verinite.auth_service.service.impl;

import com.verinite.auth_service.dto.LoginRequest;
import com.verinite.auth_service.dto.LoginResponse;
import com.verinite.auth_service.entity.User;
import com.verinite.auth_service.entity.UserSession;
import com.verinite.auth_service.exception.ResourceNotFoundException;
import com.verinite.auth_service.repository.UserRepository;
import com.verinite.auth_service.repository.UserSessionRepository;
import com.verinite.auth_service.security.JwtTokenProvider;
import com.verinite.auth_service.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final UserSessionRepository userSessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    private static final int MAX_FAILED = 5;
    private static final int LOCK_MINUTES = 15;

    @Override
    @Transactional
    public LoginResponse login(LoginRequest request,
                               String ipAddress, String userAgent) {

        User user = userRepository
                .findByUsername(request.getUsername())
                .orElseThrow(() ->
                        new RuntimeException("Invalid credentials"));

        // Check account locked
        if (user.getLockedUntil() != null
                && user.getLockedUntil().isAfter(LocalDateTime.now())) {
            throw new RuntimeException(
                    "Account locked until: " + user.getLockedUntil());
        }

        // Verify password
        if (!passwordEncoder.matches(
                request.getPassword(), user.getPasswordHash())) {
            handleFailedLogin(user);
            throw new RuntimeException("Invalid credentials");
        }

        // Reset failed count on success
        user.setFailedLoginCount(0);
        user.setLockedUntil(null);
        userRepository.save(user);

        // Generate JWT
        String jti = UUID.randomUUID().toString();
        String token;
        try {
            token = jwtTokenProvider.generateToken(
                    user.getUsername(),
                    user.getRole().name(),
                    jti);
        } catch (Exception e) {
            throw new RuntimeException(
                    "Token generation failed: " + e.getMessage());
        }

        // Save session
        UserSession session = UserSession.builder()
                .user(user)
                .jti(jti)
                .expiresAt(LocalDateTime.now().plusHours(8))
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .build();
        userSessionRepository.save(session);

        return LoginResponse.builder()
                .token(token)
                .username(user.getUsername())
                .role(user.getRole().name())
                .avatarInitials(user.getAvatarInitials())
                .build();
    }

    @Override
    @Transactional
    public void logout(String jti) {
        UserSession session = userSessionRepository
                .findByJti(jti)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Session not found"));
        session.setRevokedAt(LocalDateTime.now());
        session.setRevokeReason("LOGOUT");
        userSessionRepository.save(session);
    }

    @Override
    public boolean validateToken(String jti) {
        return userSessionRepository.findByJti(jti)
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
        userSessionRepository
                .findByUserIdAndRevokedAtIsNull(userId)
                .forEach(session -> {
                    session.setRevokedAt(LocalDateTime.now());
                    session.setRevokeReason("PASSWORD_CHANGE");
                    userSessionRepository.save(session);
                });

        user.setPasswordHash(
                passwordEncoder.encode(newPassword));
        user.setPasswordChangedAt(LocalDateTime.now());
        userRepository.save(user);
    }

    private void handleFailedLogin(User user) {
        int count = user.getFailedLoginCount() + 1;
        user.setFailedLoginCount(count);

        if (count >= MAX_FAILED) {
            user.setLockedUntil(
                    LocalDateTime.now().plusMinutes(LOCK_MINUTES));
            user.setFailedLoginCount(0);
            log.warn("Account locked: {}", user.getUsername());
        }
        userRepository.save(user);
    }
}