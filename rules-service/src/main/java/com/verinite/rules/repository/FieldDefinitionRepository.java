package com.verinite.rules.repository;

import com.verinite.rules.entity.FieldDefinition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FieldDefinitionRepository extends JpaRepository<FieldDefinition, Long> {

    // Public API — builder-visible, alive only
    List<FieldDefinition> findByProfileIdAndMtiAndIsBuilderVisibleTrueAndDeletedAtIsNull(
            Long profileId, String mti
    );

    // Internal engine — all alive fields including hidden
    List<FieldDefinition> findByProfileIdAndMtiAndDeletedAtIsNull(
            Long profileId, String mti
    );

    Optional<FieldDefinition> findByIdAndDeletedAtIsNull(Long id);

    boolean existsByProfileIdAndMtiAndDeNumberAndDeletedAtIsNull(
            Long profileId, String mti, String deNumber
    );

    List<FieldDefinition> findByProfileIdAndMti(Long profileId, String mti);

    Optional<FieldDefinition> findByProfileIdAndMtiAndDeNumberAndDeletedAtIsNull(
            Long profileId, String mti, String deNumber
    );
}