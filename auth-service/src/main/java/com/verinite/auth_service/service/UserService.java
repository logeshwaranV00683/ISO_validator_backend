package com.verinite.auth_service.service;

import com.verinite.auth_service.dto.CreateUserRequest;
import com.verinite.auth_service.dto.UserDto;

import java.util.List;

public interface UserService {
    UserDto createUser(CreateUserRequest request);
    List<UserDto> getAllUsers();
    UserDto getUserById(Long id);
    UserDto updateUser(Long id, CreateUserRequest request);
    void deleteUser(Long id);
    UserDto setActive(Long id, boolean active);
    UserDto changeRole(Long id, String role);
    void resetPassword(Long id, String newPassword);
}