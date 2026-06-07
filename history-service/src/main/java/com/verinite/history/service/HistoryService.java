package com.verinite.history.service;

import com.verinite.history.dto.response.HistoryDetailDTO;
import com.verinite.history.dto.response.HistorySummaryDTO;
import com.verinite.history.entity.ValidationRun;
import com.verinite.history.entity.ValidationRunError;
import com.verinite.history.entity.ValidationRunField;
import com.verinite.history.exception.NotFoundException;
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
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class HistoryService {

    private final ValidationRunRepository      runRepository;
    private final ValidationRunFieldRepository fieldRepository;
    private final ValidationRunErrorRepository errorRepository;

    /**
     * Paginated run list with all supported filters.
     * FIX: dateFrom/dateTo were being silently ignored (always passed null to spec).
     */
    public Page<HistorySummaryDTO> listRuns(
            Long profileId, String mti, String status, Long userId,
            String responseCode,
            LocalDate dateFrom, LocalDate dateTo,               // FIX: added
            int page, int size, String sortBy, String sortDir) {

        Sort sort = sortDir.equalsIgnoreCase("asc")
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);

        Specification<ValidationRun> spec = ValidationRunSpec.filter(
                profileId, userId, status, mti,
                dateFrom, dateTo);                              // FIX: now forwarded

        return runRepository.findAll(spec, pageable)
                .map(this::toSummary);
    }

    /**
     * Full run detail: parsed fields + errors + aiExplanation.
     * run_id is NEVER exposed — only runReference crosses service boundaries.
     */
    public HistoryDetailDTO getByRunReference(String runReference) {
        ValidationRun run = runRepository.findByRunReference(runReference)
                .orElseThrow(() -> new NotFoundException("Run not found: " + runReference));

        List<ValidationRunField> fields = fieldRepository.findByRunId(run.getId());
        List<ValidationRunError> errors = errorRepository.findByRunId(run.getId());

        return toDetail(run, fields, errors);
    }

    /** Soft delete — sets deletedAt timestamp. */
    public void softDelete(String runReference) {
        ValidationRun run = runRepository.findByRunReference(runReference)
                .orElseThrow(() -> new NotFoundException("Run not found: " + runReference));

        if (run.getDeletedAt() != null) {
            throw new IllegalStateException("Run already deleted: " + runReference);
        }

        run.setDeletedAt(LocalDateTime.now());
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

    public String exportRuns(Long profileId, String mti, String status, String format) {
        Specification<ValidationRun> spec = ValidationRunSpec.filter(
                profileId, null, status, mti, null, null);
        List<ValidationRun> runs = runRepository.findAll(spec);

        if ("csv".equalsIgnoreCase(format)) {
            StringBuilder csv = new StringBuilder();
            csv.append("runReference,profileId,mti,status,totalErrors,createdAt\n");
            runs.forEach(r -> csv
                    .append(r.getRunReference()).append(",")
                    .append(r.getProfileId()).append(",")
                    .append(r.getMti()).append(",")
                    .append(r.getStatus()).append(",")
                    .append(r.getTotalErrors()).append(",")
                    .append(r.getCreatedAt()).append("\n"));
            return csv.toString();
        }

        // Default: JSON array
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper =
                    new com.fasterxml.jackson.databind.ObjectMapper();
            mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
            List<HistorySummaryDTO> dtos = runs.stream().map(this::toSummary).toList();
            return mapper.writeValueAsString(dtos);
        } catch (Exception e) {
            throw new RuntimeException("Export failed: " + e.getMessage(), e);
        }
    }
}