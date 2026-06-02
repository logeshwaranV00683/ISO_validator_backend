package com.verinite.rules.repository;

import com.verinite.rules.entity.FieldDefinition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FieldDefinitionRepository extends JpaRepository<FieldDefinition, Long> {

    // Only returns fields where is_builder_visible = true
    List<FieldDefinition> findByProfileIdAndMtiAndIsBuilderVisibleTrue(
            Long profileId, String mti
    );

    // Returns ALL fields including hidden ones (used internally by engine)
    List<FieldDefinition> findByProfileIdAndMti(Long profileId, String mti);
}