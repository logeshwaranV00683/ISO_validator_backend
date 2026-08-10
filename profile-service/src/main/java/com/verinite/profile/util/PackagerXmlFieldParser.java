package com.verinite.profile.util;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Parses a jPOS-style GenericPackager XML (<isopackager>/<isofield>) into a
 * flat list of business data element fields, skipping DE0 (MTI) and DE1
 * (Bitmap) which are message-envelope fields, not data to validate.
 */
@Component
@Slf4j
public class PackagerXmlFieldParser {

    private static final int MTI_FIELD_ID    = 0;
    private static final int BITMAP_FIELD_ID = 1;

    public List<ParsedField> parse(String xmlContent) {
        Document doc = parseSecurely(xmlContent);

        Element root = doc.getDocumentElement();
        if (root == null || !"isopackager".equalsIgnoreCase(root.getTagName())) {
            throw new IllegalArgumentException("Invalid packager XML: root element must be <isopackager>");
        }

        NodeList fieldNodes = root.getElementsByTagName("isofield");
        List<ParsedField> fields = new ArrayList<>();

        for (int i = 0; i < fieldNodes.getLength(); i++) {
            Element el = (Element) fieldNodes.item(i);

            String idStr     = el.getAttribute("id");
            String lengthStr = el.getAttribute("length");
            String name      = el.getAttribute("name");
            String isoClass  = el.getAttribute("class");

            if (idStr == null || idStr.isBlank()) continue;

            int id;
            try {
                id = Integer.parseInt(idStr.trim());
            } catch (NumberFormatException e) {
                continue; // skip malformed entries rather than failing the whole format save
            }
            if (id == MTI_FIELD_ID || id == BITMAP_FIELD_ID) continue;
            if (id < 0 || id > 128) continue;

            Integer length = null;
            if (lengthStr != null && !lengthStr.isBlank()) {
                try {
                    length = Integer.parseInt(lengthStr.trim());
                } catch (NumberFormatException ignored) { }
            }

            fields.add(ParsedField.builder()
                    .deNumber(String.valueOf(id))
                    .fieldName(name != null && !name.isBlank() ? name.trim() : ("DE" + id))
                    .length(length)
                    .isoClass(isoClass)
                    .build());
        }

        log.info("[Format XML] Parsed {} data element fields for rules-manager sync", fields.size());
        return fields;
    }

    /** Maps a jPOS field class (e.g. org.jpos.iso.IFA_LLNUM) to this system's DataType enum name. */
    public String resolveDataType(String isoClass) {
        if (isoClass == null) return "alphanumeric";
        String upper = isoClass.toUpperCase(Locale.ROOT);
        if (upper.contains("BINARY"))  return "binary";
        if (upper.contains("AMOUNT"))  return "numeric";
        if (upper.contains("NUMERIC")) return "numeric";
        if (upper.contains("CHAR"))    return "alphanumeric";
        return "alphanumeric";
    }

    /** LLL must be checked before LL, since "LLLCHAR"/"LLLNUM" also contain "LL". */
    public boolean isLllvar(String isoClass) {
        return isoClass != null && isoClass.toUpperCase(Locale.ROOT).contains("LLL");
    }

    public boolean isLlvar(String isoClass) {
        if (isoClass == null) return false;
        String upper = isoClass.toUpperCase(Locale.ROOT);
        return upper.contains("LL") && !upper.contains("LLL");
    }

    private Document parseSecurely(String xmlContent) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            factory.setValidating(false);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);

            DocumentBuilder builder = factory.newDocumentBuilder();
            builder.setEntityResolver((publicId, systemId) -> new org.xml.sax.InputSource(new java.io.StringReader("")));

            try (InputStream is = new ByteArrayInputStream(xmlContent.getBytes(StandardCharsets.UTF_8))) {
                return builder.parse(is);
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Uploaded XML could not be parsed: " + e.getMessage(), e);
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ParsedField {
        private String deNumber;
        private String fieldName;
        private Integer length;
        private String isoClass;
    }
}