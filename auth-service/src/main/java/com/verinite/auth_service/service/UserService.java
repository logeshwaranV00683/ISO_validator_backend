package com.verinite.auth_service.service;

import com.verinite.auth_service.dto.CreateUserRequest;
import com.verinite.auth_service.dto.UpdateUserRequest;
import com.verinite.auth_service.dto.UserDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface UserService {
    UserDto createUser(CreateUserRequest request);
    Page<UserDto> getAllUsers(String role, Pageable pageable);
    List<UserDto> getAllUsersUnpaged();                 // kept for internal use
    UserDto getUserById(Long id);
    UserDto updateUser(Long id, UpdateUserRequest request);
    void deleteUser(Long id);
    UserDto setActive(Long id, boolean active);
    UserDto changeRole(Long id, String role);
    void resetPassword(Long id, String newPassword);
}