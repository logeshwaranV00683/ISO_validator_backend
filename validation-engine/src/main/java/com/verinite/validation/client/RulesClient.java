package com.verinite.validation.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;
import java.util.Map;

@FeignClient(name = "rules-service")
public interface RulesClient {

    @GetMapping("/internal/rules/profile/{profileId}")
    List<Map<String, String>> getRulesByProfile(
            @PathVariable Long profileId);
}