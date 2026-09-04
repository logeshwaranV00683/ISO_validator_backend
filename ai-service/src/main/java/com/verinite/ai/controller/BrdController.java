package com.verinite.ai.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.verinite.ai.dto.BrdDocumentDto;
import com.verinite.ai.dto.BrdExtractedConfig;
import com.verinite.ai.dto.SuggestSwitchRequest;
import com.verinite.ai.dto.SwitchSuggestionResponse;
import com.verinite.ai.entity.BrdDocument;
import com.verinite.ai.exception.NotFoundException;
import com.verinite.ai.repository.BrdDocumentRepository;
import com.verinite.ai.service.BrdConfirmService;
import com.verinite.ai.service.BrdIngestService;
import com.verinite.ai.service.SwitchPredictorService;
import com.verinite.common.dto.ApiResponse;
import com.verinite.common.enums.BrdExtractStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
@Slf4j
public class BrdController {

    private final BrdIngestService        brdIngestService;
    private final BrdConfirmService       brdConfirmService;
    private final BrdDocumentRepository   brdDocumentRepository;
    private final SwitchPredictorService  switchPredictorService;
    private final ObjectMapper            objectMapper;

    // ── BRD document lifecycle ──────────────────────────────────────────────

    @PostMapping(value = "/brd/upload", consumes = "multipart/form-data")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<BrdDocumentDto>> upload(@RequestParam("file") MultipartFile file) {
        try {
            String username = currentUsername();
            BrdDocument document = brdIngestService.ingest(file, username);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success(toDto(document), "BRD document uploaded"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.ok(ApiResponse.error(e.getMessage(), "BAD_REQUEST"));
        } catch (Exception e) {
            log.error("[BRD] Upload failed: {}", e.getMessage(), e);
            return ResponseEntity.ok(ApiResponse.error("BRD upload failed: " + e.getMessage(), "BRD_UPLOAD_FAILED"));
        }
    }

    @GetMapping("/brd")
    public ResponseEntity<ApiResponse<List<BrdDocumentDto>>> list() {
        try {
            List<BrdDocumentDto> docs = brdDocumentRepository.findAllByOrderByCreatedAtDesc()
                    .stream().map(this::toDto).toList();
            return ResponseEntity.ok(ApiResponse.success(docs, "BRD documents fetched"));
        } catch (Exception e) {
            log.error("[BRD] List failed: {}", e.getMessage(), e);
            return ResponseEntity.ok(ApiResponse.success(Collections.emptyList(), "Failed to fetch BRD documents"));
        }
    }

    @GetMapping("/brd/{id}")
    public ResponseEntity<ApiResponse<BrdDocumentDto>> getById(@PathVariable Long id) {
        try {
            BrdDocument document = findOrThrow(id);
            return ResponseEntity.ok(ApiResponse.success(toDto(document), "BRD document found"));
        } catch (NotFoundException e) {
            return ResponseEntity.ok(ApiResponse.error(e.getMessage(), "NOT_FOUND"));
        } catch (Exception e) {
            log.error("[BRD] getById failed: {}", e.getMessage(), e);
            return ResponseEntity.ok(ApiResponse.error("Failed to fetch BRD document", "BRD_FETCH_FAILED"));
        }
    }

    @GetMapping("/brd/{id}/preview")
    public ResponseEntity<ApiResponse<BrdExtractedConfig>> getPreview(@PathVariable Long id) {
        try {
            BrdDocument document = findOrThrow(id);
            if (document.getExtractedJson() == null) {
                return ResponseEntity.ok(ApiResponse.error("No extracted config available yet", "NOT_READY"));
            }
            BrdExtractedConfig config = objectMapper.readValue(document.getExtractedJson(), BrdExtractedConfig.class);
            return ResponseEntity.ok(ApiResponse.success(config, "Preview fetched"));
        } catch (NotFoundException e) {
            return ResponseEntity.ok(ApiResponse.error(e.getMessage(), "NOT_FOUND"));
        } catch (Exception e) {
            log.error("[BRD] getPreview failed: {}", e.getMessage(), e);
            return ResponseEntity.ok(ApiResponse.error("Failed to parse extracted config", "BRD_PREVIEW_FAILED"));
        }
    }

    @PutMapping("/brd/{id}/preview")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<BrdExtractedConfig>> updatePreview(
            @PathVariable Long id, @RequestBody BrdExtractedConfig edited) {
        try {
            BrdDocument document = findOrThrow(id);
            document.setExtractedJson(objectMapper.writeValueAsString(edited));
            brdDocumentRepository.save(document);
            return ResponseEntity.ok(ApiResponse.success(edited, "Preview updated"));
        } catch (NotFoundException e) {
            return ResponseEntity.ok(ApiResponse.error(e.getMessage(), "NOT_FOUND"));
        } catch (Exception e) {
            log.error("[BRD] updatePreview failed: {}", e.getMessage(), e);
            return ResponseEntity.ok(ApiResponse.error("Failed to save preview edits", "BRD_PREVIEW_SAVE_FAILED"));
        }
    }

    @PostMapping("/brd/{id}/confirm")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<BrdDocumentDto>> confirm(@PathVariable Long id) {
        try {
            String username = currentUsername();
            BrdConfirmService.ConfirmResult result = brdConfirmService.confirm(id, username);

            BrdDocumentDto dto = toDto(result.getBrdDocument());
            dto.setProfileId(result.getProfileId());
            dto.setProfileName(result.getProfileName());
            dto.setFieldDefinitionsImported(result.getFieldDefinitionsImported());
            dto.setRulesImported(result.getRulesImported());

            String message = String.format(
                    "Switch profile created, %d field definitions imported, %d rules imported",
                    result.getFieldDefinitionsImported(), result.getRulesImported());

            return ResponseEntity.ok(ApiResponse.success(dto, message));
        } catch (NotFoundException e) {
            return ResponseEntity.ok(ApiResponse.error(e.getMessage(), "NOT_FOUND"));
        } catch (IllegalStateException e) {
            return ResponseEntity.ok(ApiResponse.error(e.getMessage(), "CONFLICT"));
        } catch (Exception e) {
            log.error("[BRD] confirm failed for id={}: {}", id, e.getMessage(), e);
            return ResponseEntity.ok(ApiResponse.error(friendlyConfirmError(e), "BRD_CONFIRM_FAILED"));
        }
    }

    private String friendlyConfirmError(Exception e) {
        String raw = e.getMessage();
        if (raw == null) return "Confirm failed: unknown error";

        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("\"message\"\\s*:\\s*\"([^\"]*)\"")
                .matcher(raw);
        if (m.find()) {
            String innerMessage = m.group(1);
            if (raw.contains("profile-service")) {
                return "Could not create switch profile: " + innerMessage +
                        " — try a different Profile Name on the Review step.";
            }
            if (raw.contains("rules-service")) {
                return "Could not import field definitions/rules: " + innerMessage;
            }
            return "Confirm failed: " + innerMessage;
        }
        return "Confirm failed: " + raw;
    }




    /** Soft-delete pattern — sets status=FAILED rather than removing the row. */
    @DeleteMapping("/brd/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        try {
            BrdDocument document = findOrThrow(id);
            document.setStatus(BrdExtractStatus.FAILED);
            document.setErrorMessage("Deleted by " + currentUsername());
            brdDocumentRepository.save(document);
            return ResponseEntity.ok(ApiResponse.success(null, "BRD document removed"));
        } catch (NotFoundException e) {
            return ResponseEntity.ok(ApiResponse.error(e.getMessage(), "NOT_FOUND"));
        } catch (Exception e) {
            log.error("[BRD] delete failed: {}", e.getMessage(), e);
            return ResponseEntity.ok(ApiResponse.error("Failed to remove BRD document", "BRD_DELETE_FAILED"));
        }
    }

    // ── Public switch-suggestion endpoint (reached via gateway /ai/** route) ─

    @PostMapping("/suggest-switch")
    public ResponseEntity<ApiResponse<SwitchSuggestionResponse>> suggestSwitchPublic(
            @RequestBody SuggestSwitchRequest request) {
        try {
            SwitchSuggestionResponse response = switchPredictorService.suggest(request.getRawMessage());
            return ResponseEntity.ok(ApiResponse.success(response, "OK"));
        } catch (Exception e) {
            log.warn("[BRD] Public suggest-switch failed: {}", e.getMessage());
            return ResponseEntity.ok(ApiResponse.success(
                    SwitchSuggestionResponse.builder().suggestions(Collections.emptyList()).build(), "No suggestions"));
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private BrdDocument findOrThrow(Long id) {
        return brdDocumentRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("BRD document not found: " + id));
    }

    private String currentUsername() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return (auth != null && auth.getName() != null) ? auth.getName() : "system";
    }

    private BrdDocumentDto toDto(BrdDocument d) {
        Double confidence = null;
        List<String> warnings = null;
        if (d.getStatus() == BrdExtractStatus.COMPLETED && d.getExtractedJson() != null) {
            try {
                BrdExtractedConfig config = objectMapper.readValue(d.getExtractedJson(), BrdExtractedConfig.class);
                confidence = config.getConfidence();
                warnings = config.getWarnings();
            } catch (Exception e) {
                log.warn("[BRD] Failed to parse extractedJson for id={} while building DTO", d.getId());
            }
        }
        return BrdDocumentDto.builder()
                .id(d.getId())
                .originalFilename(d.getOriginalFilename())
                .contentType(d.getContentType())
                .status(d.getStatus() != null ? d.getStatus().name() : null)
                .progressPercent(d.getProgressPercent())
                .confidence(confidence)
                .warnings(warnings)
                .uploadedBy(d.getUploadedBy())
                .confirmedBy(d.getConfirmedBy())
                .confirmedAt(d.getConfirmedAt())
                .createdAt(d.getCreatedAt())
                .errorMessage(d.getErrorMessage())
                .build();
    }
}