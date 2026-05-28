package com.verinite.common.util;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Masks PAN: keep first 6 + last 4, replace middle with ****
public class PanMaskingUtil {

    private static final Pattern PAN_PATTERN =
            Pattern.compile("\\b(\\d{6})(\\d+)(\\d{4})\\b");

    public static String mask(String value) {
        if (value == null || value.length() < 13) return value;
        Matcher m = PAN_PATTERN.matcher(value);
        if (m.find()) {
            String middle = "*".repeat(m.group(2).length());
            return m.group(1) + middle + m.group(3);
        }
        return value;
    }

    // Call this before EVERY log statement involving card data
    public static Map<Integer, String> maskFields(Map<Integer, String> fields) {
        Map<Integer, String> masked = new HashMap<>(fields);
        // DE2 = PAN
        if (masked.containsKey(2)) {
            masked.put(2, mask(masked.get(2)));
        }
        return masked;
    }
}

