package com.verinite.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BrdDocumentDto {
    private Long id;
    private String originalFilename;
    private String contentType;
    private String status;
    private Double confidence;
    private List<String> warnings;
    private String uploadedBy;
    private String confirmedBy;
    private LocalDateTime confirmedAt;
    private LocalDateTime createdAt;
    private String errorMessage;

    // Populated only on the /confirm response — drives the frontend "success" step.
    private Long profileId;
    private String profileName;
    private Integer fieldDefinitionsImported;
    private Integer rulesImported;
}