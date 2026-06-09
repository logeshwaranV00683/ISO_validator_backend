package com.verinite.validation.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ParsedFieldDTO {
    private Integer deNumber;
    private String  fieldName;
    private String  rawValue;       // renamed from value (F10d)
    private String  displayValue;   // human-readable, e.g. "4111 **** **** 1111" for PAN (F10d)
    private boolean isPresent;      // true if this DE was in the parsed message (F10d)
    private boolean masked;
}