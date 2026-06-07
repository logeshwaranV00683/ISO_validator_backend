package com.verinite.ai.repository;

import com.verinite.ai.entity.AiRunLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AiRunLogRepository extends JpaRepository<AiRunLog, Long> {

    List<AiRunLog> findByRunReferenceOrderByCreatedAtDesc(String runReference);

    Page<AiRunLog> findAll(Pageable pageable);
}