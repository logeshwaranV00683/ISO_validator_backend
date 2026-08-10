package com.verinite.profile.client;

import com.verinite.profile.config.InternalFeignConfig;
import com.verinite.profile.dto.BulkImportFieldDefsRequest;
import com.verinite.profile.dto.BulkImportResultDto;
import com.verinite.profile.dto.BulkImportRulesRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "rules-service", configuration = InternalFeignConfig.class)
public interface RulesServiceClient {

    @PostMapping("/field-definitions/bulk-import")
    BulkImportResultDto bulkImportFieldDefinitions(@RequestBody BulkImportFieldDefsRequest request);

    @PostMapping("/rules/bulk-import")
    BulkImportResultDto bulkImportRules(@RequestBody BulkImportRulesRequest request);

    @DeleteMapping("/field-definitions/by-format")
    Integer deleteFieldDefinitionsForFormat(@RequestParam("profileId") Long profileId, @RequestParam("mti") String mti);

    @DeleteMapping("/rules/by-format")
    Integer deleteRulesForFormat(@RequestParam("profileId") Long profileId, @RequestParam("mti") String mti);
}