
package com.verinite.ai.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BrdRawXmlExtractor {

    private BrdRawXmlExtractor() {
    }

    private static final Pattern EXCLUDED_ROOT_TAGS = Pattern.compile(
            "^(isopackager|packager|html|body|head)$", Pattern.CASE_INSENSITIVE);

    private static final Pattern XML_ELEMENT = Pattern.compile(
            "<([A-Za-z][\\w:.-]*)\\b[^>]*>.*?</\\1\\s*>", Pattern.DOTALL);

    public static String extract(String brdText) {
        if (brdText == null || brdText.isBlank()) return null;

        Matcher m = XML_ELEMENT.matcher(brdText);
        while (m.find()) {
            String tagName = m.group(1);
            if (EXCLUDED_ROOT_TAGS.matcher(tagName).matches()) {
                continue;
            }
            String candidate = m.group().trim();
            if (candidate.length() < 20) {
                continue;
            }
            return candidate;
        }
        return null;
    }
}