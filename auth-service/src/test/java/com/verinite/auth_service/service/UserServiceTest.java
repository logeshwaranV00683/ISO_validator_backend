package com.verinite.auth_service.service;

import com.verinite.auth_service.dto.CreateUserRequest;
import com.verinite.auth_service.dto.UserDto;
import com.verinite.auth_service.entity.Role;
import com.verinite.auth_service.entity.User;
import com.verinite.auth_service.exception.ResourceNotFoundException;
import com.verinite.auth_service.repository.UserRepository;
import com.verinite.auth_service.service.impl.UserServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserServiceImpl userService;

    private User mockUser;
    private CreateUserRequest createRequest;

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
                .build();

        createRequest = new CreateUserRequest();
        createRequest.setUsername("bala");
        createRequest.setPassword("password123");
        createRequest.setFullName("Bala R");
        createRequest.setRole(Role.ADMIN);
    }

    // ─── createUser ───────────────────────────────────────

    @Test
    void createUser_Success() {
        when(userRepository.findByUsername("bala"))
                .thenReturn(Optional.empty());
        when(passwordEncoder.encode("password123"))
                .thenReturn("hashed123");
        when(userRepository.save(any(User.class)))
                .thenReturn(mockUser);

        UserDto result = userService.createUser(createRequest);

        assertThat(result).isNotNull();
        assertThat(result.getUsername()).isEqualTo("bala");
        assertThat(result.getAvatarInitials()).isEqualTo("BR");
        assertThat(result.getRole()).isEqualTo(Role.ADMIN);
        verify(userRepository).save(any(User.class));
    }

    @Test
    void createUser_DuplicateUsername_ThrowsException() {
        when(userRepository.findByUsername("bala"))
                .thenReturn(Optional.of(mockUser));

        assertThatThrownBy(() ->
                userService.createUser(createRequest))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Username already exists");

        verify(userRepository, never()).save(any());
    }

    // ─── getAllUsers ───────────────────────────────────────

    @Test
    void getAllUsers_ReturnsActiveUsers() {
        when(userRepository.findByDeletedAtIsNull())
                .thenReturn(List.of(mockUser));

        List<UserDto> result = userService.getAllUsers();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getUsername()).isEqualTo("bala");
    }

    @Test
    void getAllUsers_Empty_ReturnsEmptyList() {
        when(userRepository.findByDeletedAtIsNull())
                .thenReturn(List.of());

        List<UserDto> result = userService.getAllUsers();

        assertThat(result).isEmpty();
    }

    // ─── getUserById ──────────────────────────────────────

    @Test
    void getUserById_Found_ReturnsDto() {
        when(userRepository.findById(1L))
                .thenReturn(Optional.of(mockUser));

        UserDto result = userService.getUserById(1L);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getUsername()).isEqualTo("bala");
    }

    @Test
    void getUserById_NotFound_ThrowsException() {
        when(userRepository.findById(99L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                userService.getUserById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    // ─── updateUser ───────────────────────────────────────

    @Test
    void updateUser_Success_UpdatesFields() {
        CreateUserRequest updateRequest = new CreateUserRequest();
        updateRequest.setUsername("bala");
        updateRequest.setPassword("pass");
        updateRequest.setFullName("Bala Rajan");
        updateRequest.setRole(Role.ANALYST);

        User updatedUser = User.builder()
                .id(1L)
                .username("bala")
                .passwordHash("hashed123")
                .fullName("Bala Rajan")
                .avatarInitials("BR")
                .role(Role.ANALYST)
                .active(true)
                .build();

        when(userRepository.findById(1L))
                .thenReturn(Optional.of(mockUser));
        when(userRepository.save(any(User.class)))
                .thenReturn(updatedUser);

        UserDto result = userService.updateUser(1L, updateRequest);

        assertThat(result.getFullName()).isEqualTo("Bala Rajan");
        assertThat(result.getRole()).isEqualTo(Role.ANALYST);
    }

    @Test
    void updateUser_NotFound_ThrowsException() {
        when(userRepository.findById(99L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                userService.updateUser(99L, createRequest))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ─── deleteUser ───────────────────────────────────────

    @Test
    void deleteUser_Success_SoftDelete() {
        when(userRepository.findById(1L))
                .thenReturn(Optional.of(mockUser));
        when(userRepository.save(any(User.class)))
                .thenReturn(mockUser);

        userService.deleteUser(1L);

        assertThat(mockUser.getDeletedAt()).isNotNull();
        verify(userRepository).save(mockUser);
    }

    @Test
    void deleteUser_NotFound_ThrowsException() {
        when(userRepository.findById(99L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                userService.deleteUser(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}