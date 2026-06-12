package com.verinite.history.repository;

import com.verinite.common.enums.Severity;
import com.verinite.history.entity.ValidationRunError;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ValidationRunErrorRepository extends JpaRepository<ValidationRunError, Long> {

    List<ValidationRunError> findByRunId_Id(Long runId);

    List<ValidationRunError> findByRunId_IdAndSeverity(
            Long runId,
            Severity severity);
}