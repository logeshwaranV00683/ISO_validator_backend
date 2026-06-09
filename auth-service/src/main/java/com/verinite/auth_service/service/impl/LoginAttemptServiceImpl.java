package com.verinite.auth_service.service.impl;

import com.verinite.auth_service.entity.User;
import com.verinite.auth_service.repository.UserRepository;
import com.verinite.auth_service.service.LoginAttemptService;
import com.verinite.auth_service.service.SystemConfigService;
import com.verinite.auth_service.util.AuthConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
@RequiredArgsConstructor
@Slf4j
public class LoginAttemptServiceImpl implements LoginAttemptService {

    private final UserRepository      userRepository;
    private final SystemConfigService systemConfigService;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailedAttempt(Long userId) {

        // Read from DB — falls back to AuthConstants if not configured
        int maxFailures = systemConfigService.getInt("login.max.failures",  AuthConstants.MAX_FAILURES);
        int lockMinutes = systemConfigService.getInt("account.lock.minutes", AuthConstants.LOCK_MINUTES);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        int failures = user.getFailedLoginCount() + 1;

        if (failures >= maxFailures) {
            user.setLockedUntil(LocalDateTime.now(ZoneOffset.UTC).plusMinutes(lockMinutes));
            user.setFailedLoginCount(failures);
            log.warn("Account locked: userId={}", userId);
        } else {
            user.setFailedLoginCount(failures);
            log.warn("Failed login attempt {}/{}: userId={}", failures, maxFailures, userId);
        }

        userRepository.save(user);
    }
}