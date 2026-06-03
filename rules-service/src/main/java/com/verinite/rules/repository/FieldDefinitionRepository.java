package com.verinite.rules.repository;

import com.verinite.rules.entity.FieldDefinition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FieldDefinitionRepository extends JpaRepository<FieldDefinition, Long> {

    // Public API — visible fields only
    List<FieldDefinition> findByProfileIdAndMtiAndIsBuilderVisibleTrueAndIsDeletedFalse(
            Long profileId, String mti
    );

    // Internal engine use — ALL non-deleted (including hidden fields)
    List<FieldDefinition> findByProfileIdAndMtiAndIsDeletedFalse(
            Long profileId, String mti
    );

    Optional<FieldDefinition> findByIdAndIsDeletedFalse(Long id);

    boolean existsByProfileIdAndMtiAndDeNumberAndIsDeletedFalse(
            Long profileId, String mti, String deNumber
    );
}