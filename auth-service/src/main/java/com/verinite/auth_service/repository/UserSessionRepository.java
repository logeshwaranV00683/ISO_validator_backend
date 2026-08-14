package com.verinite.auth_service.repository;

import com.verinite.auth_service.entity.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserSessionRepository extends JpaRepository<UserSession, Long> {

    Optional<UserSession> findByJti(String jti);

    List<UserSession> findByUserIdAndRevokedAtIsNull(Long userId);

    List<UserSession> findByUserIdAndRevokedAtIsNullOrderByIssuedAtAsc(Long userId);


    List<UserSession> findByUserIdOrderByIssuedAtDesc(Long userId);
}