package com.verinite.auth_service.service.impl;


import com.verinite.auth_service.dto.CreateUserRequest;
import com.verinite.auth_service.dto.UserDto;
import com.verinite.auth_service.entity.User;
import com.verinite.auth_service.exception.ResourceNotFoundException;
import com.verinite.auth_service.repository.UserRepository;
import com.verinite.auth_service.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

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
    public UserDto createUser(CreateUserRequest request) {

        boolean exists = userRepository
                .findByUsername(request.getUsername())
                .isPresent();

        if (exists) {
            throw new RuntimeException("Username already exists");
        }

        String initials = Arrays.stream(
                        request.getFullName().split(" "))
                .map(word ->
                        String.valueOf(word.charAt(0))
                                .toUpperCase())
                .collect(Collectors.joining());

        User user = User.builder()
                .username(request.getUsername())
                .passwordHash(
                        passwordEncoder.encode(
                                request.getPassword()))
                .fullName(request.getFullName())
                .avatarInitials(initials)
                .role(request.getRole())
                .build();

        User savedUser =
                userRepository.save(user);

        return mapToDto(savedUser);
    }

    @Override
    public List<UserDto> getAllUsers() {

        return userRepository
                .findByDeletedAtIsNull()
                .stream()
                .map(this::mapToDto)
                .toList();
    }

    @Override
    public void deleteUser(Long id) {
        User user = userRepository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
        user.setDeletedAt(LocalDateTime.now());
        userRepository.save(user);
    }

    private UserDto mapToDto(User user) {

        return UserDto.builder()
                .id(user.getId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .avatarInitials(
                        user.getAvatarInitials())
                .role(user.getRole())
                .active(user.isActive())
                .build();
    }

    @Override
    public UserDto getUserById(Long id) {

        User user = userRepository
                .findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "User not found with id: " + id));

        return mapToDto(user);
    }

    @Override
    public UserDto updateUser(Long id, CreateUserRequest request) {

        User user = userRepository
                .findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "User not found with id: " + id));

        // Update initials if fullName changed
        String initials = Arrays.stream(
                        request.getFullName().split(" "))
                .map(word ->
                        String.valueOf(word.charAt(0))
                                .toUpperCase())
                .collect(Collectors.joining());

        user.setFullName(request.getFullName());
        user.setAvatarInitials(initials);
        user.setRole(request.getRole());

        User updatedUser = userRepository.save(user);
        return mapToDto(updatedUser);
    }

}
