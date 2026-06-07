package com.verinite.ai.repository;

import com.verinite.ai.entity.AiPromptTemplateVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AiPromptTemplateVersionRepository extends JpaRepository<AiPromptTemplateVersion, Long> {

    Optional<AiPromptTemplateVersion> findByTemplateIdAndIsCurrentTrue(Long templateId);

    /** Used by rollback to fetch the content of a specific past version. */
    Optional<AiPromptTemplateVersion> findByTemplateIdAndVersionNumber(Long templateId, int versionNumber);

    /** Full version history for a template, sorted newest first. */
    List<AiPromptTemplateVersion> findByTemplateIdOrderByVersionNumberDesc(Long templateId);
}