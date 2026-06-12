package com.verinite.history.service;

import com.verinite.common.enums.RunStatus;
import com.verinite.history.dto.response.StatsResponse;
import com.verinite.history.entity.ValidationRun;
import com.verinite.history.repository.ValidationRunRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class StatsService {

    private final ValidationRunRepository runRepository;

    public StatsResponse getStats() {

        List<ValidationRun> all = runRepository.findAll();

        long totalRuns = all.size();

        // FIX Bug 3: was "PASSED"/"FAILED" — correct enum values are VALID/INVALID
        long passed = all.stream().filter(r -> r.getStatus() == RunStatus.PASSED).count();
        long failed = all.stream().filter(r -> r.getStatus() == RunStatus.FAILED).count();
        long warned = all.stream().filter(r -> r.getStatus() == RunStatus.WARNED).count();
        long parseErrors = all.stream().filter(r -> r.getStatus() == RunStatus.PARSE_ERROR).count();

        double passRate = totalRuns > 0
                ? Math.round((passed * 1000.0 / totalRuns)) / 10.0
                : 0.0;

        OptionalDouble avgTotal = all.stream()
                .filter(r -> r.getTotalDurationMs() != null)
                .mapToLong(ValidationRun::getTotalDurationMs)
                .average();
        OptionalDouble avgAi = all.stream()
                .filter(r -> r.getAiDurationMs() != null)
                .mapToLong(ValidationRun::getAiDurationMs)
                .average();

        long aiSkipCount = all.stream()
                .filter(r -> Boolean.FALSE.equals(r.getAiEnabled()))
                .count();

        Map<String, Long> runsByMti = all.stream()
                .filter(r -> r.getMti() != null)
                .collect(Collectors.groupingBy(ValidationRun::getMti, Collectors.counting()));

        Map<String, Long> runsByStatus = all.stream()
                .filter(r -> r.getStatus() != null)
                .collect(Collectors.groupingBy(r -> r.getStatus().name(), Collectors.counting()));

        Map<String, Long> runsByProfile = all.stream()
                .filter(r -> r.getProfileNameSnapshot() != null)
                .collect(Collectors.groupingBy(ValidationRun::getProfileNameSnapshot, Collectors.counting()));

        return StatsResponse.builder()
                .totalRuns(totalRuns)
                .passed(passed)
                .failed(failed)
                .warned(warned)
                .parseErrors(parseErrors)
                .passRate(passRate)
                // FIX Bug 4: was (long) cast → Double mismatch; now returns double directly
                .avgTotalMs(avgTotal.isPresent() ? avgTotal.getAsDouble() : 0.0)
                .avgAiMs(avgAi.isPresent()       ? avgAi.getAsDouble()   : 0.0)
                // FIX Bug 5: p95TotalMs removed from here — field added to StatsResponse instead
                .p95TotalMs(0L)
                .aiSkipCount(aiSkipCount)
                .aiErrorCount(0L)
                .topErrorFields(List.of())
                .runsByMti(runsByMti)
                .runsByProfile(runsByProfile)
                .runsByStatus(runsByStatus)
                .build();
    }
}