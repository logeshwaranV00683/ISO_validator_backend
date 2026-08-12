package com.verinite.auth_service.service;

import com.verinite.auth_service.dto.CreateUserRequest;
import com.verinite.auth_service.dto.UpdateUserRequest;
import com.verinite.auth_service.dto.UserDto;
import com.verinite.auth_service.entity.User;
import com.verinite.auth_service.exception.ResourceNotFoundException;
import com.verinite.auth_service.repository.UserRepository;
import com.verinite.auth_service.service.impl.UserServiceImpl;
import com.verinite.common.enums.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserServiceImpl userService;

    private User mockUser;
    private CreateUserRequest createRequest; // FIX ①: was `UpdateUserRequest` — they're separate classes

    @BeforeEach
    void setUp() {
        mockUser = User.builder()
                .id(1L)
                .username("testuser")
                .passwordHash("hashed123")
                .fullName("Test User")
                .avatarInitials("TU")
                .role(Role.ADMIN)
                .active(true)
                .build();

        createRequest = new CreateUserRequest();
        createRequest.setUsername("testuser");
        createRequest.setPassword("password123");
        createRequest.setFullName("Test User");
        createRequest.setRole(Role.ADMIN);
    }

    // ─── createUser ───────────────────────────────────────

    @Test
    void createUser_Success() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("password123")).thenReturn("hashed123");
        when(userRepository.save(any(User.class))).thenReturn(mockUser);

        UserDto result = userService.createUser(createRequest);

        assertThat(result).isNotNull();
        assertThat(result.getUsername()).isEqualTo("testuser");
        assertThat(result.getAvatarInitials()).isEqualTo("TU");
        assertThat(result.getRole()).isEqualTo(Role.ADMIN);
        verify(userRepository).save(any(User.class));
    }

    @Test
    void createUser_DuplicateUsername_ThrowsException() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(mockUser));

        assertThatThrownBy(() -> userService.createUser(createRequest))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Username already exists");

        verify(userRepository, never()).save(any());
    }

    // ─── getAllUsers ───────────────────────────────────────

    @Test
    void getAllUsers_ReturnsActiveUsers() {
        Pageable pageable = PageRequest.of(
                0,
                20,
                Sort.by("username").ascending()
        );

        Page<User> userPage = new PageImpl<>(List.of(mockUser), pageable, 1);

        when(userRepository.findByDeletedAtIsNull(pageable))
                .thenReturn(userPage);

        Page<UserDto> result = userService.getAllUsers(null, pageable);

        assertThat(result).hasSize(1);
        assertThat(result.getContent().getFirst().getUsername())
                .isEqualTo("testuser");
    }

    @Test
    void getAllUsers_Empty_ReturnsEmptyList() {
        Pageable pageable = PageRequest.of(
                0,
                20,
                Sort.by("username").ascending()
        );

        when(userRepository.findByDeletedAtIsNull(pageable))
                .thenReturn(Page.empty(pageable));

        assertThat(userService.getAllUsers(null, pageable))
                .isEmpty();
    }


    // ─── getUserById ──────────────────────────────────────

    @Test
    void getUserById_Found_ReturnsDto() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));

        UserDto result = userService.getUserById(1L);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getUsername()).isEqualTo("testuser");
    }

    @Test
    void getUserById_NotFound_ThrowsException() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUserById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    // ─── updateUser ───────────────────────────────────────

    @Test
    void updateUser_Success_UpdatesFields() {
        // FIX ②: was `new CreateUserRequest()` assigned to UpdateUserRequest — wrong type
        UpdateUserRequest updateRequest = new UpdateUserRequest();
        updateRequest.setFullName("Updated Name");
        updateRequest.setRole(Role.ANALYST);

        User updatedUser = User.builder()
                .id(1L)
                .username("testuser")
                .passwordHash("hashed123")
                .fullName("Updated Name")
                .avatarInitials("TU")
                .role(Role.ANALYST)
                .active(true)
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
        when(userRepository.save(any(User.class))).thenReturn(updatedUser);

        UserDto result = userService.updateUser(1L, updateRequest);

        assertThat(result.getFullName()).isEqualTo("Updated Name");
        assertThat(result.getRole()).isEqualTo(Role.ANALYST);
    }

    @Test
    void updateUser_NotFound_ThrowsException() {
        // FIX ②: was passing `createRequest` (CreateUserRequest) where UpdateUserRequest expected
        UpdateUserRequest updateRequest = new UpdateUserRequest();
        updateRequest.setFullName("Any Name");
        updateRequest.setRole(Role.ANALYST);

        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.updateUser(99L, updateRequest))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ─── deleteUser ───────────────────────────────────────

    @Test
    void deleteUser_Success_SoftDelete() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
        when(userRepository.save(any(User.class))).thenReturn(mockUser);

        userService.deleteUser(1L);

        assertThat(mockUser.getDeletedAt()).isNotNull();
        verify(userRepository).save(mockUser);
    }

    @Test
    void deleteUser_NotFound_ThrowsException() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.deleteUser(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}