package com.verinite.ai.repository;

import com.verinite.ai.entity.AiPromptTemplateVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface AiPromptTemplateVersionRepository extends JpaRepository<AiPromptTemplateVersion, Long> {

    Optional<AiPromptTemplateVersion> findByTemplateIdAndIsCurrentTrue(Long templateId);
}