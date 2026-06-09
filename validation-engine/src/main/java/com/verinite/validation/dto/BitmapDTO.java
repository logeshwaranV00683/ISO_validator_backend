package com.verinite.validation.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class BitmapDTO {
    private String        primary;   // 16-char hex (64 bits)
    private String        extended;  // renamed from secondary — 16-char hex, null if no DEs 65-128
    private List<Integer> bitsSet;   // bit positions that are ON, e.g. [2, 3, 11, 22]
}