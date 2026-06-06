package com.verinite.validation.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import java.util.List;
import java.util.Map;

@FeignClient(name = "rules-service")
public interface RulesClient {

    @GetMapping("/internal/rules/effective")
    List<Map<String, Object>> getEffectiveRules(
            @RequestParam Long profileId,
            @RequestParam String mti);
}