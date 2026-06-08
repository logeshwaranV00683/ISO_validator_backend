package com.verinite.validation.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * FIX: Flattened event published to validation.events exchange.
 *
 * Previous version had nested `timing`, `bitmap`, `aiResult` objects.
 * history-service ValidationRunConsumer reads all fields at the top level
 * from a Map<String, Object>, so nesting caused all timing, AI, and summary
 * counts to be silently null in the DB.
 *
 * Nested DTO classes for parsedFields and errors are preserved but field names
 * are aligned with what the consumer expects.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ValidationRunEvent {

    // ── Core identifiers ────────────────────────────────────────────────────
    private String runReference;
    private String status;              // PASSED | FAILED | WARNED | PARSE_ERROR
    private Long   profileId;
    private String profileNameSnapshot;
    private Long   formatId;
    private String formatNameSnapshot;
    private String userId;
    private String usernameSnapshot;
    private String userRoleSnapshot;
    private String rawMessage;          // never logged — stored for audit
    private String correlationId;
    private String clientIp;

    // ── Message metadata ────────────────────────────────────────────────────
    private String  mti;
    private String  mtiDescription;
    private String  bitmapPrimary;      // FIX: was nested in bitmap.primary
    private String  bitmapExtended;     // FIX: was nested in bitmap.secondary

    // ── Summary counts ──────────────────────────────────────────────────────
    private Integer totalFieldsPresent; // FIX: was missing
    private Integer totalFieldsParsed;
    private Integer totalErrors;        // FIX: was missing
    private Integer criticalCount;      // FIX: was missing
    private Integer warningCount;       // FIX: was missing
    private Integer infoCount;          // FIX: was missing

    // ── Key field extracts ──────────────────────────────────────────────────
    private String  responseCode;       // DE39
    private String  responseLabel;
    private Long    transactionAmount;  // DE4 parsed as Long
    private String  currencyCode;       // DE49
    private String  merchantName;       // DE43
    private String  terminalId;         // DE41
    private String  panMasked;          // DE2 masked

    // ── Timing (FIX: was nested in timing object) ───────────────────────────
    private Integer parseDurationMs;
    private Integer validationDurationMs;
    private Integer totalDurationMs;

    // ── AI (FIX: was nested in aiResult object) ─────────────────────────────
    private Boolean aiEnabled;
    private Integer aiDurationMs;       // FIX: was aiResult.durationMs
    private String  aiExplanation;      // FIX: was aiResult.explanation
    private String  aiModelUsed;        // FIX: was aiResult.modelUsed

    // ── Rerun tracking ──────────────────────────────────────────────────────
    private Boolean isRerun;
    private String  originalRunReference;

    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    // ── Nested field/error lists ─────────────────────────────────────────────
    private List<ParsedFieldEvent>     parsedFields;
    private List<ValidationErrorEvent> errors;

    /**
     * Field names match what ValidationRunConsumer reads from the Map.
     */
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ParsedFieldEvent {
        private String  deNumber;       // "DE2"
        private String  fieldName;
        private String  rawValue;       // FIX: consumer reads "rawValue", ParsedFieldDTO had "value"
        private String  displayValue;
        private Boolean isPresent;
        private Integer fieldLength;
        private Integer dePosition;
        private String  encodingType;
    }

    /**
     * Field names match what ValidationRunConsumer reads from the Map.
     */
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ValidationErrorEvent {
        private Long    ruleId;
        private String  deNumber;
        private String  fieldName;
        private String  severity;
        private String  errorCode;
        private String  errorMessage;   // FIX: consumer reads "errorMessage"; DTO had "message"
        private String  ruleSnapshot;
        private String  expectedValue;
        private String  actualValue;
        private String  aiExplanation;
        private String  aiFixSuggestion;
    }
}