package com.verinite.validation.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Mirrors rules-service's FieldDefinition entity JSON.
 * Used by ValidationServiceImpl.buildMessage() for POST /api/v1/validate/build.
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class FieldDefinitionDto {

    private Long    id;
    private Long    profileId;
    private String  mti;
    private String  deNumber;        // e.g. "DE2"
    private String  fieldName;
    private String  dataType;        // NUMERIC / ALPHA / ALPHANUMERIC / BINARY / SPECIAL
    private Integer maxLength;

    // ── Boolean flags ─────────────────────────────────────────────────────────
    // @JsonAlias covers BOTH Jackson naming conventions from rules-service:
    //   • entity uses Boolean wrapper  → Lombok generates getIsLlvar()  → JSON key "isLlvar"
    //   • entity uses boolean primitive → Lombok generates isLlvar()     → JSON key "llvar"  ← stripped by Jackson
    // Without @JsonAlias the primitive-entity case silently maps to null here.

    @JsonAlias({"llvar", "isLlvar"})
    private Boolean isLlvar = false;

    @JsonAlias({"lllvar", "isLllvar"})
    private Boolean isLllvar = false;

    @JsonAlias({"mandatory", "isMandatory"})
    private Boolean isMandatory = false;

    @JsonAlias({"builderVisible", "isBuilderVisible"})
    private Boolean isBuilderVisible = true;

    private String  placeholderValue;
    private Integer displayOrder = 0;

    // ── DO NOT add `private String lengthType` — no such DB column exists ────
    // The service calls fd.getLengthType() expecting a computed result.
    // Lombok @Data does NOT override manually defined getters, so this is safe.

    /**
     * Derives encoding type from the is_llvar / is_lllvar DB flags.
     * Called by ValidationServiceImpl.buildMessage() at lines 373–374.
     *
     * Returns: "LLLVAR" | "LLVAR" | "FIXED"
     */
    public String getLengthType() {
        if (Boolean.TRUE.equals(isLllvar)) return "LLLVAR";
        if (Boolean.TRUE.equals(isLlvar))  return "LLVAR";
        return "FIXED";
    }
}