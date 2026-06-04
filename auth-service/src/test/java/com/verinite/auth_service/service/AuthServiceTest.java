package com.verinite.auth_service.service;

import com.verinite.auth_service.dto.LoginRequest;
import com.verinite.auth_service.dto.LoginResponse;
import com.verinite.auth_service.entity.User;
import com.verinite.auth_service.entity.UserSession;
import com.verinite.auth_service.exception.ResourceNotFoundException;
import com.verinite.auth_service.repository.UserRepository;
import com.verinite.auth_service.repository.UserSessionRepository;
import com.verinite.auth_service.service.impl.AuthServiceImpl;
import com.verinite.common.enums.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserSessionRepository userSessionRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AuthServiceImpl authService;

    private User mockUser;
    private LoginRequest loginRequest;

    @BeforeEach
    void setUp() {
        mockUser = User.builder()
                .id(1L)
                .username("bala")
                .passwordHash("hashed123")
                .fullName("Bala R")
                .avatarInitials("BR")
                .role(Role.ADMIN)
                .active(true)
                .failedLoginCount(0)
                .build();

        loginRequest = new LoginRequest();
        loginRequest.setUsername("bala");
        loginRequest.setPassword("password123");
    }

    // ─── login ────────────────────────────────────────────

    @Test
    void login_Success_ReturnsToken() throws Exception {
        when(userRepository.findByUsername("bala"))
                .thenReturn(Optional.of(mockUser));
        when(passwordEncoder.matches("password123", "hashed123"))
                .thenReturn(true);
        when(userRepository.save(any(User.class)))
                .thenReturn(mockUser);
        when(userSessionRepository.save(any(UserSession.class)))
                .thenReturn(new UserSession());

        LoginResponse response = authService.login(
                loginRequest, "127.0.0.1", "PostmanTest");

        assertThat(response).isNotNull();
        assertThat(response.getToken()).isEqualTo("mock.jwt.token");
        assertThat(response.getUsername()).isEqualTo("bala");
        assertThat(response.getRole()).isEqualTo("ADMIN");
    }

    @Test
    void login_UserNotFound_ThrowsException() {
        when(userRepository.findByUsername("bala"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                authService.login(loginRequest, "127.0.0.1", "agent"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Invalid credentials");
    }

    @Test
    void login_WrongPassword_ThrowsException() {
        when(userRepository.findByUsername("bala"))
                .thenReturn(Optional.of(mockUser));
        when(passwordEncoder.matches("password123", "hashed123"))
                .thenReturn(false);
        when(userRepository.save(any(User.class)))
                .thenReturn(mockUser);

        assertThatThrownBy(() ->
                authService.login(loginRequest, "127.0.0.1", "agent"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Invalid credentials");
    }

    @Test
    void login_AccountLocked_ThrowsException() {
        mockUser.setLockedUntil(
                LocalDateTime.now().plusMinutes(10));

        when(userRepository.findByUsername("bala"))
                .thenReturn(Optional.of(mockUser));

        assertThatThrownBy(() ->
                authService.login(loginRequest, "127.0.0.1", "agent"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Account locked");
    }

    @Test
    void login_FiveFailedAttempts_LocksAccount() {
        mockUser.setFailedLoginCount(4);

        when(userRepository.findByUsername("bala"))
                .thenReturn(Optional.of(mockUser));
        when(passwordEncoder.matches("password123", "hashed123"))
                .thenReturn(false);
        when(userRepository.save(any(User.class)))
                .thenReturn(mockUser);

        assertThatThrownBy(() ->
                authService.login(loginRequest, "127.0.0.1", "agent"))
                .isInstanceOf(RuntimeException.class);

        assertThat(mockUser.getLockedUntil()).isNotNull();
    }

    // ─── logout ───────────────────────────────────────────

    @Test
    void logout_Success_RevokesSession() {
        UserSession session = UserSession.builder()
                .jti("test-jti-123")
                .build();

        when(userSessionRepository.findByJti("test-jti-123"))
                .thenReturn(Optional.of(session));
        when(userSessionRepository.save(any(UserSession.class)))
                .thenReturn(session);

        authService.logout("test-jti-123");

        assertThat(session.getRevokedAt()).isNotNull();
        assertThat(session.getRevokeReason()).isEqualTo("LOGOUT");
    }

    @Test
    void logout_SessionNotFound_ThrowsException() {
        when(userSessionRepository.findByJti("invalid-jti"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                authService.logout("invalid-jti"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Session not found");
    }

    // ─── changePassword ───────────────────────────────────

    @Test
    void changePassword_Success() {
        UserSession activeSession = UserSession.builder()
                .jti("jti-1")
                .build();

        when(userRepository.findById(1L))
                .thenReturn(Optional.of(mockUser));
        when(passwordEncoder.matches("password123", "hashed123"))
                .thenReturn(true);
        when(passwordEncoder.encode("newPassword"))
                .thenReturn("newHashed");
        when(userSessionRepository
                .findByUserIdAndRevokedAtIsNull(1L))
                .thenReturn(List.of(activeSession));
        when(userSessionRepository.save(any()))
                .thenReturn(activeSession);
        when(userRepository.save(any()))
                .thenReturn(mockUser);

        authService.changePassword(1L, "password123", "newPassword");

        assertThat(mockUser.getPasswordHash()).isEqualTo("newHashed");
        assertThat(activeSession.getRevokeReason())
                .isEqualTo("PASSWORD_CHANGE");
    }

    @Test
    void changePassword_WrongCurrentPassword_ThrowsException() {
        when(userRepository.findById(1L))
                .thenReturn(Optional.of(mockUser));
        when(passwordEncoder.matches("wrongPass", "hashed123"))
                .thenReturn(false);

        assertThatThrownBy(() ->
                authService.changePassword(1L, "wrongPass", "new"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Current password is incorrect");
    }

    @Test
    void changePassword_UserNotFound_ThrowsException() {
        when(userRepository.findById(99L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                authService.changePassword(99L, "pass", "new"))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}