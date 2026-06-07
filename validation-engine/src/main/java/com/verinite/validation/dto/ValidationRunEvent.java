package com.verinite.validation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Published to RabbitMQ validation.events exchange after every validation run.
 * Consumed by history-service to persist the run.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidationRunEvent {
    private String runReference;
    private String status;
    private Long   profileId;
    private String mti;
    /** Raw hex message — stored for audit; NEVER logged */
    private String rawMessage;
    private String userId;
    private String correlationId;

    private List<ParsedFieldDTO>     parsedFields;  // PAN already masked
    private List<ValidationErrorDTO> errors;
    private TimingDTO  timing;
    private AiResultDTO aiResult;

    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();
}