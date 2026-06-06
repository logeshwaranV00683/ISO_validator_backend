package com.verinite.ai.repository;

import com.verinite.ai.entity.OllamaConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface OllamaConfigRepository extends JpaRepository<OllamaConfig, Long> {

    Optional<OllamaConfig> findByConfigKey(String configKey);
}