package com.verinite.history_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidationRunFieldDto {

    private String deNumber;
    private String fieldName;
    private String rawValue;
    private String displayValue;
    private boolean isPresent;
    private Integer fieldLength;
    private Integer dePosition;
    private String encodingType;
}
