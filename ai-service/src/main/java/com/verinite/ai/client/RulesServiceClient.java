package com.verinite.ai.client;

import com.verinite.ai.config.InternalFeignConfig;
import com.verinite.ai.dto.BulkImportFieldDefsRequest;
import com.verinite.ai.dto.BulkImportResultDto;
import com.verinite.ai.dto.BulkImportRulesRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "rules-service", configuration = InternalFeignConfig.class)
public interface RulesServiceClient {

    @PostMapping("/field-definitions/bulk-import")
    BulkImportResultDto bulkImportFieldDefinitions(@RequestBody BulkImportFieldDefsRequest request);

    @PostMapping("/rules/bulk-import")
    BulkImportResultDto bulkImportRules(@RequestBody BulkImportRulesRequest request);
}