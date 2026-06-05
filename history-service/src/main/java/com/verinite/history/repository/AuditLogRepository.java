package com.verinite.history.repository;

import com.verinite.history.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface AuditLogRepository
        extends JpaRepository<AuditLog, Long>,
        JpaSpecificationExecutor<AuditLog> {  // needed for AuditLogSpec filters

    Page<AuditLog> findByAction(String action, Pageable pageable);
    Page<AuditLog> findBySourceService(String sourceService, Pageable pageable);
    Page<AuditLog> findByEntityType(String entityType, Pageable pageable);
    Page<AuditLog> findByUserId(Long userId, Pageable pageable);
    Page<AuditLog> findByCreatedAtBetween(LocalDateTime from, LocalDateTime to, Pageable pageable);
    Page<AuditLog> findByActionAndCreatedAtBetween(
            String action, LocalDateTime from, LocalDateTime to, Pageable pageable);

    // INSERT ONLY — no update or delete methods, ever.
}