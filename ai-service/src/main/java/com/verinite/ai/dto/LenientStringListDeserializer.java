package com.verinite.ai.dto;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Small local models (e.g. gemma3:1b) don't reliably stick to "warnings": ["string", ...].
 * They sometimes emit "warnings": [{"message": "..."}] or [{"warning": "...", "field": "..."}]
 * or even numbers/nulls. Rather than blowing up the whole BRD extraction over a warnings
 * array formatting slip, coerce every element to a best-effort String.
 */
public class LenientStringListDeserializer extends JsonDeserializer<List<String>> {

    @Override
    public List<String> deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        JsonNode node = p.getCodec().readTree(p);
        List<String> result = new ArrayList<>();
        if (node == null || node.isNull()) {
            return result;
        }
        if (!node.isArray()) {
            // single scalar/object where an array was expected — treat as one element
            result.add(nodeToString(node));
            return result;
        }
        for (JsonNode element : node) {
            if (element == null || element.isNull()) continue;
            result.add(nodeToString(element));
        }
        return result;
    }

    private String nodeToString(JsonNode element) {
        if (element.isTextual()) {
            return element.asText();
        }
        if (element.isObject()) {
            ObjectNode obj = (ObjectNode) element;
            // common shapes the model tends to emit
            for (String key : new String[]{"message", "warning", "text", "detail"}) {
                if (obj.has(key) && obj.get(key).isTextual()) {
                    return obj.get(key).asText();
                }
            }
            return obj.toString();
        }
        return element.asText();
    }
}