package com.verinite.history.repository;

import com.verinite.history.entity.ValidationRunError;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ValidationRunErrorRepository extends JpaRepository<ValidationRunError, Long> {

    List<ValidationRunError> findByRunId(Long runId);

    // FIX Bug 8: was String severity — must match entity field type which is the Severity enum
    List<ValidationRunError> findByRunIdAndSeverity(Long runId, ValidationRunError.Severity severity);
}