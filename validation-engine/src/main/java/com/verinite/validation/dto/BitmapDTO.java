package com.verinite.validation.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BitmapDTO {
    private String primary;    // 16-char hex string (64 bits)
    private String secondary;  // 16-char hex string — null if no fields 65–128 present
}