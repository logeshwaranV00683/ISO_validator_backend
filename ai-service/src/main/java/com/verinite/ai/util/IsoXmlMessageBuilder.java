package com.verinite.ai.util;

import com.verinite.ai.dto.BrdExtractedConfig;

import java.util.List;
public final class IsoXmlMessageBuilder {

    private IsoXmlMessageBuilder() {
    }

    public static String build(BrdExtractedConfig config) {
        if (config == null) return null;

        String mti = safe(config.getMti(), "0200");
        List<BrdExtractedConfig.BrdFieldDefinitionDto> fields = config.getFieldDefinitions();

        StringBuilder xml = new StringBuilder();
        xml.append("<isoMessage>\n");
        xml.append("  <mti>").append(escape(mti)).append("</mti>\n");
        xml.append("  <fields>\n");

        if (fields != null) {
            for (BrdExtractedConfig.BrdFieldDefinitionDto f : fields) {
                if (f == null || isBlank(f.getDeNumber())) continue;

                String de = escape(f.getDeNumber());
                String name = escape(safe(f.getFieldName(), ""));
                String type = escape(safe(f.getDataType(), "numeric"));
                Integer maxLength = f.getMaxLength();
                boolean mandatory = Boolean.TRUE.equals(f.getIsMandatory());
                boolean llvar = Boolean.TRUE.equals(f.getIsLlvar());
                boolean lllvar = Boolean.TRUE.equals(f.getIsLllvar());

                String sampleValue = sampleValue(type, maxLength);

                xml.append("    <field")
                        .append(" de=\"").append(de).append("\"")
                        .append(" name=\"").append(name).append("\"")
                        .append(" type=\"").append(type).append("\"")
                        .append(" length=\"").append(maxLength == null ? "" : maxLength).append("\"")
                        .append(" mandatory=\"").append(mandatory).append("\"")
                        .append(" llvar=\"").append(llvar).append("\"")
                        .append(" lllvar=\"").append(lllvar).append("\"")
                        .append(">")
                        .append(escape(sampleValue))
                        .append("</field>\n");
            }
        }

        xml.append("  </fields>\n");
        xml.append("</isoMessage>");
        return xml.toString();
    }

    private static String sampleValue(String dataType, Integer maxLength) {
        int len = (maxLength == null || maxLength <= 0) ? 6 : Math.min(maxLength, 19);
        String type = dataType == null ? "numeric" : dataType.toLowerCase();
        char fillChar;
        switch (type) {
            case "alpha":
                fillChar = 'X';
                break;
            case "alphanumeric":
            case "special":
                fillChar = 'A';
                break;
            case "binary":
                fillChar = '0';
                break;
            case "numeric":
            default:
                fillChar = '1';
                break;
        }
        return String.valueOf(fillChar).repeat(len);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String safe(String s, String fallback) {
        return isBlank(s) ? fallback : s;
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}