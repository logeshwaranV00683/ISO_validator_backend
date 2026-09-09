package com.verinite.ai.service;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.verinite.ai.client.OllamaClient;
import com.verinite.ai.dto.BrdExtractedConfig;
import com.verinite.ai.entity.AiPromptTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
@Service
@RequiredArgsConstructor
@Slf4j
public class BrdExtractionService {

    private static final Pattern JSON_BLOCK = Pattern.compile("\\{[\\s\\S]*}");
    private final AiTemplateService aiTemplateService;
    private final OllamaClient      ollamaClient;
    private final ObjectMapper      objectMapper;

    public BrdExtractedConfig extract(String brdText) {
        // Detect this BEFORE the LLM call, and independent of whether the LLM call
        // succeeds - some BRDs (like ones exported from an existing switch config)
        // paste a complete, already-authored <isopackager> XML at the end of the
        // document. The LLM is only prompted to return the prose-derived JSON schema,
        // so it never surfaces this XML; without this, it gets silently discarded and
        // BrdConfirmService instead reconstructs a partial XML from the much smaller
        // "FIELD DEFINITIONS" prose section.
        String embeddedXml = extractEmbeddedPackagerXml(brdText);

        try {
            AiPromptTemplate template = aiTemplateService.resolveBrdTemplate();
            String prompt = template.getPromptTemplate().replace("{brd_text}", brdText == null ? "" : brdText);
            String rawResponse = ollamaClient.callOllamaJson(prompt);
            if (rawResponse == null || rawResponse.isBlank()) {
                return emptyConfig("Ollama returned an empty response for BRD extraction.", embeddedXml);
            }

            String jsonText = extractJsonBlock(rawResponse);
            BrdExtractedConfig config = objectMapper.readValue(jsonText, BrdExtractedConfig.class);

            if (config.getFieldDefinitions() == null) config.setFieldDefinitions(new ArrayList<>());
            if (config.getRules() == null) config.setRules(new ArrayList<>());
            if (config.getWarnings() == null) config.setWarnings(new ArrayList<>());
            if (config.getConfidence() == null) config.setConfidence(0.0);

            sanitizeFieldNames(config);


            config.setEmbeddedPackagerXml(embeddedXml);
            if (embeddedXml != null) {
                List<String> warnings = new ArrayList<>(config.getWarnings());
                warnings.add("Detected an embedded packager XML in the BRD document (" + countIsofields(embeddedXml) +
                        " fields) - it will be used as-is instead of a reconstructed XML. Review it before confirming: " +
                        "if it's meant to be a full reference packager rather than this switch's exact field set, trim it manually.");
                config.setWarnings(warnings);
            }

            return config;
        } catch (Exception e) {
            log.warn("[BRD] Extraction failed, degrading to empty config: {}", e.getMessage());
            return emptyConfig("AI extraction failed: " + e.getMessage(), embeddedXml);
        }
    }
    private static final Pattern EMBEDDED_PACKAGER_XML = Pattern.compile(
            "(?:\\s*(?:<\\?xml[^>]*\\?>|<!DOCTYPE\\s+isopackager\\b[\\s\\S]*?>|<!--[\\s\\S]*?-->))*\\s*<isopackager\\b[\\s\\S]*?</isopackager>",
            Pattern.CASE_INSENSITIVE);


    private String extractEmbeddedPackagerXml(String brdText) {
        if (brdText == null || brdText.isBlank()) return null;
        Matcher m = EMBEDDED_PACKAGER_XML.matcher(brdText);
        if (!m.find()) return null;

        String candidate = m.group().trim();
        // Basic well-formedness check: parse it before trusting it as the packager XML.
        try {
            javax.xml.parsers.DocumentBuilderFactory factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setExpandEntityReferences(false);
            javax.xml.parsers.DocumentBuilder builder = factory.newDocumentBuilder();
            builder.setEntityResolver((publicId, systemId) -> new org.xml.sax.InputSource(new java.io.StringReader("")));
            builder.parse(new org.xml.sax.InputSource(new java.io.StringReader(candidate)));
        } catch (Exception e) {
            log.warn("[BRD] Found an <isopackager> block in the document but it isn't well-formed XML, ignoring it: {}", e.getMessage());
            return null;
        }

        // Ensure it has an XML declaration for downstream consumers that expect one.
        if (!candidate.startsWith("<?xml")) {
            candidate = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + candidate;
        }
        return candidate;
    }

    private static final Pattern ISOFIELD_ELEMENT = Pattern.compile("<isofield\\b[^>]*?/>", Pattern.DOTALL);

    /** Counts &lt;isofield .../&gt; elements, purely for the informational warning shown on Review. */
    private int countIsofields(String xml) {
        if (xml == null) return 0;
        Matcher m = ISOFIELD_ELEMENT.matcher(xml);
        int count = 0;
        while (m.find()) count++;
        return count;
    }

    /**
     * Local/small LLMs sometimes ignore the "short label only" instruction in the
     * BRD_PARSE prompt and instead stuff the *entire field definition* into
     * fieldName, e.g. "Primary Account Number, numeric, LLVAR, max 19, mandatory".
     * That string then flows straight into the generated packager XML's name="..."
     * attribute, which is the "field definition showing up in the XML" bug.
     *
     * This trims each fieldName down to just the descriptive label: it cuts at the
     * first delimiter that typically introduces type/length/flag metadata (comma,
     * semicolon, colon, pipe, parenthesis) and drops it entirely if what's left
     * still looks like metadata rather than a name.
     */
    private void sanitizeFieldNames(BrdExtractedConfig config) {
        if (config.getFieldDefinitions() != null) {
            for (BrdExtractedConfig.BrdFieldDefinitionDto f : config.getFieldDefinitions()) {
                f.setFieldName(cleanFieldName(f.getFieldName(), f.getDeNumber()));
            }
        }
        if (config.getRules() != null) {
            for (BrdExtractedConfig.BrdRuleDto r : config.getRules()) {
                r.setFieldName(cleanFieldName(r.getFieldName(), r.getDeNumber()));
            }
        }
    }

    private static final Pattern METADATA_DELIMITER = Pattern.compile("[,;:|(].*", Pattern.DOTALL);
    private static final Pattern METADATA_KEYWORDS = Pattern.compile(
            "\\b(numeric|alpha|alphanumeric|binary|llvar|lllvar|mandatory|optional|max\\s*length|maxlength|length|de\\d+)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final int MAX_FIELD_NAME_LENGTH = 60;

    private String cleanFieldName(String rawName, String deNumber) {
        String fallback = deNumber != null && !deNumber.isBlank() ? deNumber : "Unnamed Field";
        if (rawName == null || rawName.isBlank()) return fallback;

        String name = rawName.trim();

        // Cut off anything after a delimiter commonly used to append type/length/flags
        // e.g. "Primary Account Number, numeric, LLVAR, max 19" -> "Primary Account Number"
        Matcher delim = METADATA_DELIMITER.matcher(name);
        if (delim.matches()) {
            name = name.substring(0, delim.start()).trim();
        }

        // If what remains is still empty, too long, or reads like a spec dump rather
        // than a label, the model didn't follow the schema - fall back to the DE number
        // rather than let a garbled string reach the generated XML.
        if (name.isBlank()
                || name.length() > MAX_FIELD_NAME_LENGTH
                || METADATA_KEYWORDS.matcher(name).find()) {
            log.warn("[BRD] Discarding malformed fieldName from extraction (deNumber={}): \"{}\"",
                    deNumber, rawName);
            return fallback;
        }

        return name;
    }

    private String extractJsonBlock(String text) {
        Matcher m = JSON_BLOCK.matcher(text);
        if (m.find()) {
            return m.group();
        }
        return text;
    }

    private BrdExtractedConfig emptyConfig(String warning) {
        return emptyConfig(warning, null);
    }

    private BrdExtractedConfig emptyConfig(String warning, String embeddedXml) {
        List<String> warnings = new ArrayList<>();
        warnings.add(warning);
        if (embeddedXml != null) {
            warnings.add("AI field extraction failed, but a valid embedded packager XML was found in the document and will still be used.");
        }
        return BrdExtractedConfig.builder()
                .switchProfile(BrdExtractedConfig.BrdSwitchProfileDto.builder()
                        .profileName("Untitled BRD Profile")
                        .description("")
                        .environment("DEV")
                        .build())
                .mti(null)
                .fieldDefinitions(new ArrayList<>())
                .rules(new ArrayList<>())
                .confidence(0.0)
                .warnings(warnings)
                .embeddedPackagerXml(embeddedXml)
                .build();
    }
}