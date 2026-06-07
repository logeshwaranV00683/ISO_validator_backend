package com.verinite.auth_service.service;

import com.verinite.auth_service.dto.LoginRequest;
import com.verinite.auth_service.dto.LoginResponse;
import com.verinite.auth_service.entity.User;

public interface AuthService {
    LoginResponse login(LoginRequest request, String ipAddress, String userAgent);
    LoginResponse refreshToken(User user, String ipAddress, String userAgent);
    void logout(String jti);
    boolean validateToken(String jti);
    void changePassword(Long userId, String currentPassword, String newPassword);
}