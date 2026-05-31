package com.verinite.auth_service.service;

import com.verinite.auth_service.dto.LoginRequest;
import com.verinite.auth_service.dto.LoginResponse;

public interface AuthService {
    LoginResponse login(LoginRequest request, String ipAddress, String userAgent);
    void logout(String jti);
}