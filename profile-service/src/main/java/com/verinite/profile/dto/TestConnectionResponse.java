package com.verinite.profile.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class TestConnectionResponse {
    private Long          profileId;
    private String        host;
    private Integer       port;
    private String        result;        // OK | FAILED
    private String        message;
    private Integer       latencyMs;
    private LocalDateTime testedAt;
}