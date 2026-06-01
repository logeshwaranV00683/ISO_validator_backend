package com.verinite.auth_service.service;

public interface LoginAttemptService {
    void recordFailedAttempt(Long userId);
}