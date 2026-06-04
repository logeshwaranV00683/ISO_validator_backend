package com.verinite.rules.repository;

import com.verinite.rules.entity.RuleAllowedValue;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RuleAllowedValueRepository extends JpaRepository<RuleAllowedValue, Long> {

    Optional<RuleAllowedValue> findByRuleIdAndAllowedValue(Long ruleId, String allowedValue);

    @Modifying
    @Transactional
    @Query("DELETE FROM RuleAllowedValue av WHERE av.rule.id = :ruleId AND av.allowedValue = :value")
    void deleteByRuleIdAndAllowedValue(
            @Param("ruleId") Long ruleId,
            @Param("value")  String value
    );
}