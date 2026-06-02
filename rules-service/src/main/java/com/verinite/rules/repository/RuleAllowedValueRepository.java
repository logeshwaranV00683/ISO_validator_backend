package com.verinite.rules.repository;

import com.verinite.rules.entity.RuleAllowedValue;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RuleAllowedValueRepository extends JpaRepository<RuleAllowedValue, Long> {

    Optional<RuleAllowedValue> findByRuleIdAndValue(Long ruleId, String value);

    void deleteByRuleIdAndValue(Long ruleId, String value);
}