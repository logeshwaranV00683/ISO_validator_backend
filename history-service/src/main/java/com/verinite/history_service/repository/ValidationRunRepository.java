package com.verinite.history.repository;

import com.verinite.history.entity.ValidationRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ValidationRunRepository extends JpaRepository<ValidationRun, Long> {

    boolean existsByRunReference(String runReference);

    Optional<ValidationRun> findByRunReference(String runReference);
}