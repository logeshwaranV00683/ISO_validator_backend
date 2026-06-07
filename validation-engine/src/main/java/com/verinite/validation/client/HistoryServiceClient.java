package com.verinite.validation.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "history-service", path = "/history")
public interface HistoryServiceClient {

    @GetMapping("/runs/{runReference}")
    Object getRunDetail(@PathVariable("runReference") String runReference);
}