package com.verinite.validation.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Represents one parsed ISO 8583 Data Element in the response.
 */
@Data
@Builder
public class ParsedFieldDTO {
    private Integer deNumber;
    private String  fieldName;
    private String  value;
    private boolean masked;  // true for DE2 (PAN)
}