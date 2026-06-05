package com.verinite.ai.repository;

import com.verinite.ai.entity.AiPromptTemplate;
import com.verinite.common.enums.TemplateScope;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface AiPromptTemplateRepository extends JpaRepository<AiPromptTemplate, Long> {

    Optional<AiPromptTemplate> findByProfileIdAndActiveTrueAndDeletedAtIsNull(Long profileId);

    Optional<AiPromptTemplate> findFirstByScopeAndActiveTrueAndDeletedAtIsNull(TemplateScope scope);

    List<AiPromptTemplate> findAllByDeletedAtIsNull();
}