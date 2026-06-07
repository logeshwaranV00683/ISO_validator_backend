package com.verinite.common.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ApiError {
    private String code;
    private String message;
    private List<FieldErrorDetail> details;
    private String traceId;
}