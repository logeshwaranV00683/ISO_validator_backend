package com.verinite.auth_service.service.impl;

import com.verinite.auth_service.util.AuthConstants;
import com.verinite.auth_service.entity.User;
import com.verinite.auth_service.repository.UserRepository;
import com.verinite.auth_service.service.LoginAttemptService;
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

    private final UserRepository userRepository;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailedAttempt(Long userId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        int failures = user.getFailedLoginCount() + 1;

        if (failures >= AuthConstants.MAX_FAILURES) {
            user.setLockedUntil(
                    LocalDateTime.now(ZoneOffset.UTC).plusMinutes(AuthConstants.LOCK_MINUTES)
            );
            user.setFailedLoginCount(failures); // retain count — reset only on successful login

            log.warn("Account locked: userId={}", userId);
        } else {
            user.setFailedLoginCount(failures);

            log.warn(
                    "Failed login attempt {}/{}: userId={}",
                    failures,
                    AuthConstants.MAX_FAILURES,
                    userId
            );
        }

        userRepository.save(user);
    }
}