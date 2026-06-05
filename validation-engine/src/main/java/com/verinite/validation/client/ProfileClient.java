package com.verinite.validation.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Map;

@FeignClient(name = "profile-service")
public interface ProfileClient {

    @GetMapping("/internal/profiles/{id}")
    Map<String, Object> getProfileById(@PathVariable Long id);
}