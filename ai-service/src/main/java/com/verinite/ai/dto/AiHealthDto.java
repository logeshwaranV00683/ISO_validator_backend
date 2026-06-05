package com.verinite.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @AllArgsConstructor @NoArgsConstructor
public class AiHealthDto {
    private String status;      // UP or DOWN
    private String model;       // mistral:7b
    private String endpoint;    // http://localhost:11434
}