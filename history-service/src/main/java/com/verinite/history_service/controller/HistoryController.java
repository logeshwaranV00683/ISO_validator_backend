package com.verinite.history.controller;

import com.verinite.common.dto.ApiResponse;
import com.verinite.history.entity.ValidationRun;
import com.verinite.history.repository.ValidationRunRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/history")
@RequiredArgsConstructor
@Slf4j
public class HistoryController {

    private final ValidationRunRepository validationRunRepository;

    @GetMapping("/{runReference}")
    public ResponseEntity<ApiResponse<ValidationRun>> getByRunReference(
            @PathVariable String runReference) {

        return validationRunRepository.findByRunReference(runReference)
                .map(run -> ResponseEntity.ok(
                        ApiResponse.success(run, "Run found")))
                .orElseGet(() -> ResponseEntity.status(404).body(
                        ApiResponse.error("Run not found: " + runReference, "RUN_NOT_FOUND")));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ValidationRun>>> listRuns() {
        List<ValidationRun> runs = validationRunRepository.findAll()
                .stream()
                .filter(r -> r.getDeletedAt() == null)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(runs, "Runs fetched"));
    }
}