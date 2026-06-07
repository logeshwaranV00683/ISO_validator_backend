package com.verinite.validation.client;

import com.verinite.validation.dto.EffectiveRuleDto;
import com.verinite.validation.dto.FieldDefinitionDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "rules-service")
public interface RulesClient {

    /**
     * Returns active, date-windowed, priority-sorted rules for the given profile+MTI.
     */
    @GetMapping("/internal/rules/effective")
    List<EffectiveRuleDto> getEffectiveRules(
            @RequestParam Long   profileId,
            @RequestParam String mti);

    /**
     * Returns field definitions for message builder.
     */
    @GetMapping("/internal/field-definitions")
    List<FieldDefinitionDto> getFieldDefinitions(
            @RequestParam Long   profileId,
            @RequestParam String mti);
}