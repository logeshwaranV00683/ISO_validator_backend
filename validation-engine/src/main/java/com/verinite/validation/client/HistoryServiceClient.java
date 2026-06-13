package com.verinite.validation.client;

import com.verinite.common.dto.ApiResponse;
import com.verinite.common.dto.HistoryDetailDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "history-service")
public interface HistoryServiceClient {

    @GetMapping("/runs/{runReference}")
    ApiResponse<HistoryDetailDTO> getRunDetail(@PathVariable String runReference);
}