package com.verinite.ai.repository;

import com.verinite.ai.entity.AiPromptTemplate;
import com.verinite.common.enums.TemplateScope;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AiPromptTemplateRepository extends JpaRepository<AiPromptTemplate, Long> {

    /** PROFILE scope resolution — find an active, non-deleted template for a specific profile. */
    Optional<AiPromptTemplate> findByProfileIdAndActiveTrueAndDeletedAtIsNull(Long profileId);

    /** GLOBAL scope fallback — find the first active global template. */
    Optional<AiPromptTemplate> findFirstByScopeAndActiveTrueAndDeletedAtIsNull(TemplateScope scope);

    /** List all active, non-deleted templates. */
    List<AiPromptTemplate> findAllByDeletedAtIsNull();

    /** List templates by scope (GLOBAL or PROFILE). */
    List<AiPromptTemplate> findByScopeAndDeletedAtIsNull(TemplateScope scope);
}