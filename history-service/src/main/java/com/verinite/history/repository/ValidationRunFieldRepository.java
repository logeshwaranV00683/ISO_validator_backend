package com.verinite.history.repository;

import com.verinite.history.entity.ValidationRunField;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ValidationRunFieldRepository extends JpaRepository<ValidationRunField, Long> {

    List<ValidationRunField> findByRunId_Id(Long runId);
}