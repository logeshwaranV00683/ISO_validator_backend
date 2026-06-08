package com.verinite.auth_service.service.impl;

import com.verinite.auth_service.dto.CreateUserRequest;
import com.verinite.auth_service.dto.UpdateUserRequest;
import com.verinite.auth_service.dto.UserDto;
import com.verinite.auth_service.entity.User;
import com.verinite.auth_service.exception.ResourceNotFoundException;
import com.verinite.auth_service.repository.UserRepository;
import com.verinite.auth_service.service.UserService;
import com.verinite.common.enums.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public UserDto createUser(CreateUserRequest request) {
        if (userRepository.findByUsername(request.getUsername()).isPresent()) {
            throw new RuntimeException("Username already exists: " + request.getUsername());
        }
        User user = User.builder()
                .username(request.getUsername())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .email(request.getEmail() != null ? request.getEmail() : null)
                .avatarInitials(initials(request.getFullName()))
                .role(request.getRole())
                .build();
        return mapToDto(userRepository.save(user));
    }

    @Override
    public Page<UserDto> getAllUsers(Pageable pageable) {
        List<UserDto> all = userRepository.findByDeletedAtIsNull()
                .stream().map(this::mapToDto).toList();

        int start = (int) pageable.getOffset();
        int end   = Math.min(start + pageable.getPageSize(), all.size());
        List<UserDto> slice = (start >= all.size()) ? List.of() : all.subList(start, end);
        return new PageImpl<>(slice, pageable, all.size());
    }

    @Override
    public List<UserDto> getAllUsersUnpaged() {
        return userRepository.findByDeletedAtIsNull().stream()
                .map(this::mapToDto).toList();
    }

    @Override
    public UserDto getUserById(Long id) {
        return mapToDto(findActive(id));
    }

    // BUG 2 FIX: accepts UpdateUserRequest — never touches password
    @Override
    @Transactional
    public UserDto updateUser(Long id, UpdateUserRequest request) {
        User user = findActive(id);
        user.setFullName(request.getFullName());
        user.setAvatarInitials(initials(request.getFullName()));
        user.setEmail(request.getEmail());
        user.setRole(request.getRole());
        return mapToDto(userRepository.save(user));
    }

    @Override
    @Transactional
    public void deleteUser(Long id) {
        User user = findActive(id);
        user.setDeletedAt(LocalDateTime.now());
        userRepository.save(user);
    }

    @Override
    @Transactional
    public UserDto setActive(Long id, boolean active) {
        User user = findActive(id);
        user.setActive(active);
        return mapToDto(userRepository.save(user));
    }

    @Override
    @Transactional
    public UserDto changeRole(Long id, String role) {
        User user = findActive(id);
        try {
            user.setRole(Role.valueOf(role.toUpperCase()));
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Invalid role: " + role + ". Valid: ADMIN, ANALYST, VIEWER");
        }
        return mapToDto(userRepository.save(user));
    }

    @Override
    @Transactional
    public void resetPassword(Long id, String newPassword) {
        User user = findActive(id);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setPasswordChangedAt(LocalDateTime.now());
        user.setFailedLoginCount(0);
        user.setLockedUntil(null);
        userRepository.save(user);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private User findActive(Long id) {
        return userRepository.findById(id)
                .filter(u -> u.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
    }

    private String initials(String fullName) {
        return Arrays.stream(fullName.trim().split("\\s+"))
                .map(w -> String.valueOf(w.charAt(0)).toUpperCase())
                .collect(Collectors.joining());
    }

    private UserDto mapToDto(User user) {
        return UserDto.builder()
                .id(user.getId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .avatarInitials(user.getAvatarInitials())
                .role(user.getRole())
                .active(user.isActive())
                .build();
    }
}