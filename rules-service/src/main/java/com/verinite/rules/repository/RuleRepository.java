package com.verinite.rules.repository;

import com.verinite.rules.entity.ValidationRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RuleRepository extends JpaRepository<ValidationRule, Long> {

    @Query("""
        SELECT r FROM ValidationRule r
        WHERE r.profileId = :profileId
          AND r.mti = :mti
          AND r.active = true
          AND r.deletedAt IS NULL
        ORDER BY r.fieldId, r.ruleType
    """)
    List<ValidationRule> findEffectiveRules(
            @Param("profileId") Long profileId,
            @Param("mti") String mti
    );
}