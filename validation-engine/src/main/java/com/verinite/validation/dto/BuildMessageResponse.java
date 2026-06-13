package com.verinite.validation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class BuildMessageResponse {
    private String       rawMessage;           // renamed from hexMessage (F10g)
    private String       mti;
    private String       mtiDescription;       // added (F10g)
    private Long         profileId;
    private String       profile;              // profile name (F10g)
    private String       bitmapHex;            // primary bitmap hex (F10g)
    private List<Integer> bitsSet;             // which DEs are set (F10g)
    private int          totalLength;          // total message length in bytes (F10g)
    private List<FieldBreakdown> fieldBreakdown; // per-field detail (F10g)
    private List<String> missingMandatory;     // mandatory DEs not provided (F10g)
    private List<String> validationWarnings;
    @Schema(description = "Actual output format used: HEX or ASCII")
    private String outputFormat;

    @Data
    @Builder
    public static class FieldBreakdown {
        private Integer deNumber;
        private String  fieldName;
        private String  rawValue;
        private String  encoding;   // "FIXED" | "LLVAR" | "LLLVAR"
    }
}