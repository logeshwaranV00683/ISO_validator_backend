package com.verinite.history.service;

import com.verinite.history.dto.response.HistoryDetailDTO;
import com.verinite.history.dto.response.HistorySummaryDTO;
import com.verinite.history.entity.ValidationRun;
import com.verinite.history.entity.ValidationRunError;
import com.verinite.history.entity.ValidationRunField;
import com.verinite.history.repository.ValidationRunErrorRepository;
import com.verinite.history.repository.ValidationRunFieldRepository;
import com.verinite.history.repository.ValidationRunRepository;
import com.verinite.history.spec.ValidationRunSpec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class HistoryService {

    private final ValidationRunRepository runRepository;
    private final ValidationRunFieldRepository fieldRepository;
    private final ValidationRunErrorRepository errorRepository;

    public Page<HistorySummaryDTO> listRuns(
            Long profileId, String mti, String status, Long userId,
            String responseCode, int page, int size, String sortBy, String sortDir) {

        Sort sort = sortDir.equalsIgnoreCase("asc")
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);

        // FIX Bug 6: was runRepository.findAll(pageable) — filters were completely ignored
        Specification<ValidationRun> spec = ValidationRunSpec.filter(
                profileId, userId, status, mti, null, null);
        Page<ValidationRun> runs = runRepository.findAll(spec, pageable);
        return runs.map(this::toSummary);
    }

    public HistoryDetailDTO getByRunReference(String runReference) {
        ValidationRun run = runRepository.findByRunReference(runReference)
                .orElseThrow(() -> new RuntimeException("Run not found: " + runReference));

        List<ValidationRunField> fields = fieldRepository.findByRunId(run.getId());
        List<ValidationRunError> errors = errorRepository.findByRunId(run.getId());

        return toDetail(run, fields, errors);
    }

    public void softDelete(String runReference) {
        ValidationRun run = runRepository.findByRunReference(runReference)
                .orElseThrow(() -> new RuntimeException("Run not found: " + runReference));

        // FIX Bug 7: guard against double-delete
        if (run.getDeletedAt() != null) {
            throw new RuntimeException("Run already deleted: " + runReference);
        }

        run.setDeletedAt(java.time.LocalDateTime.now());
        runRepository.save(run);
        log.info("Soft deleted run: {}", runReference);
    }

    // ── Mappers ───────────────────────────────────────────────────────────────

    private HistorySummaryDTO toSummary(ValidationRun r) {
        return HistorySummaryDTO.builder()
                .runId(r.getId())
                .runReference(r.getRunReference())
                .profileId(r.getProfileId())
                .profileNameSnapshot(r.getProfileNameSnapshot())
                .formatId(r.getFormatId())
                .formatNameSnapshot(r.getFormatNameSnapshot())
                .userId(r.getUserId())
                .usernameSnapshot(r.getUsernameSnapshot())
                .userRoleSnapshot(r.getUserRoleSnapshot())
                .mti(r.getMti())
                .mtiDescription(r.getMtiDescription())
                .status(r.getStatus())
                .totalFieldsPresent(r.getTotalFieldsPresent())
                .totalErrors(r.getTotalErrors())
                .criticalCount(r.getCriticalCount())
                .warningCount(r.getWarningCount())
                .infoCount(r.getInfoCount())
                .responseCode(r.getResponseCode())
                .responseLabel(r.getResponseLabel())
                .panMasked(r.getPanMasked())
                .transactionAmount(r.getTransactionAmount())
                .currencyCode(r.getCurrencyCode())
                .totalDurationMs(r.getTotalDurationMs())
                .aiEnabled(r.getAiEnabled())
                .createdAt(r.getCreatedAt())
                .build();
    }

    private HistoryDetailDTO toDetail(ValidationRun r,
                                      List<ValidationRunField> fields,
                                      List<ValidationRunError> errors) {
        return HistoryDetailDTO.builder()
                .runId(r.getId())
                .runReference(r.getRunReference())
                .profileId(r.getProfileId())
                .profileNameSnapshot(r.getProfileNameSnapshot())
                .formatId(r.getFormatId())
                .formatNameSnapshot(r.getFormatNameSnapshot())
                .userId(r.getUserId())
                .usernameSnapshot(r.getUsernameSnapshot())
                .userRoleSnapshot(r.getUserRoleSnapshot())
                .mti(r.getMti())
                .mtiDescription(r.getMtiDescription())
                .bitmapPrimary(r.getBitmapPrimary())
                .bitmapExtended(r.getBitmapExtended())
                .status(r.getStatus())
                .totalFieldsPresent(r.getTotalFieldsPresent())
                .totalErrors(r.getTotalErrors())
                .criticalCount(r.getCriticalCount())
                .warningCount(r.getWarningCount())
                .infoCount(r.getInfoCount())
                .responseCode(r.getResponseCode())
                .responseLabel(r.getResponseLabel())
                .transactionAmount(r.getTransactionAmount())
                .currencyCode(r.getCurrencyCode())
                .merchantName(r.getMerchantName())
                .terminalId(r.getTerminalId())
                .panMasked(r.getPanMasked())
                .parseDurationMs(r.getParseDurationMs())
                .validationDurationMs(r.getValidationDurationMs())
                .aiDurationMs(r.getAiDurationMs())
                .totalDurationMs(r.getTotalDurationMs())
                .aiEnabled(r.getAiEnabled())
                .aiExplanation(r.getAiExplanation())
                .aiModelUsed(r.getAiModelUsed())
                .isRerun(r.getIsRerun())
                .originalRunReference(r.getOriginalRunReference())
                .clientIp(r.getClientIp())
                .correlationId(r.getCorrelationId())
                .createdAt(r.getCreatedAt())
                .parsedFields(fields.stream().map(f -> HistoryDetailDTO.FieldDto.builder()
                        .deNumber(f.getDeNumber())
                        .fieldName(f.getFieldName())
                        .rawValue(f.getRawValue())
                        .displayValue(f.getDisplayValue())
                        .isPresent(f.getIsPresent())
                        .fieldLength(f.getFieldLength())
                        .dePosition(f.getDePosition())
                        .encodingType(f.getEncodingType())
                        .build()).toList())
                .errors(errors.stream().map(e -> HistoryDetailDTO.ErrorDto.builder()
                        .ruleId(e.getRuleId())
                        .deNumber(e.getDeNumber())
                        .fieldName(e.getFieldName())
                        .severity(e.getSeverity())
                        .errorCode(e.getErrorCode())
                        .issueDescription(e.getErrorMessage())
                        .ruleSnapshot(e.getRuleSnapshot())
                        .expectedValue(e.getExpectedValue())
                        .actualValue(e.getActualValue())
                        .aiExplanation(e.getAiExplanation())
                        .aiFixSuggestion(e.getAiFixSuggestion())
                        .build()).toList())
                .build();
    }
}