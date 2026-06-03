package com.verinite.rules.repository;

import com.verinite.rules.entity.ValidationRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface RuleRepository extends JpaRepository<ValidationRule, Long> {

    /**
     * Effective rules: active, not deleted, date-windowed, sorted by priority.
     * Called by validation-engine via internal Feign endpoint.
     */
    @Query("""
        SELECT r FROM ValidationRule r
        WHERE r.profileId   = :profileId
          AND r.mti         = :mti
          AND r.active      = true
          AND r.isDeleted   = false
          AND (r.effectiveFrom IS NULL OR r.effectiveFrom <= :today)
          AND (r.effectiveTo   IS NULL OR r.effectiveTo   >= :today)
        ORDER BY r.priority ASC
    """)
    List<ValidationRule> findEffectiveRules(
            @Param("profileId") Long profileId,
            @Param("mti")       String mti,
            @Param("today")     LocalDate today
    );

    /**
     * All non-deleted rules for a profile+mti — used for export and bulk-replace pre-deletion.
     */
    @Query("""
        SELECT r FROM ValidationRule r
        WHERE r.profileId = :profileId
          AND r.mti       = :mti
          AND r.isDeleted = false
        ORDER BY r.priority ASC
    """)
    List<ValidationRule> findAllNonDeleted(
            @Param("profileId") Long profileId,
            @Param("mti")       String mti
    );

    Optional<ValidationRule> findByIdAndIsDeletedFalse(Long id);

    boolean existsByProfileIdAndMtiAndDeNumberAndIsDeletedFalse(
            Long profileId, String mti, String deNumber
    );
}