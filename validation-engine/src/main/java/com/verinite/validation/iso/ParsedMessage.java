package com.verinite.validation.iso;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Map;

/**
 * Value object returned by IsoParserUtil.parse().
 * Immutable snapshot of one parsed ISO 8583 message.
 */
@Data
@AllArgsConstructor
public class ParsedMessage {
    private String              mti;
    private Map<Integer, String> fields;
}