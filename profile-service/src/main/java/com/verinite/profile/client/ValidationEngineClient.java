package com.verinite.profile.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "validation-engine")
public interface ValidationEngineClient {

    @GetMapping("/internal/formats/{formatId}")
    String getFormatStatus(@PathVariable Long formatId);
}