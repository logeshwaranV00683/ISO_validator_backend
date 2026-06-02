package com.verinite.rules.service;

import com.verinite.rules.entity.FieldDefinition;
import com.verinite.rules.repository.FieldDefinitionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FieldDefinitionService {

    private final FieldDefinitionRepository fieldDefinitionRepository;

    // For public API — hides fields where is_builder_visible = false
    public List<FieldDefinition> getVisibleFields(Long profileId, String mti) {
        return fieldDefinitionRepository
                .findByProfileIdAndMtiAndIsBuilderVisibleTrue(profileId, mti);
    }

    // For internal engine use — returns everything including hidden fields
    public List<FieldDefinition> getByProfileAndMti(Long profileId, String mti) {
        return fieldDefinitionRepository.findByProfileIdAndMti(profileId, mti);
    }
}