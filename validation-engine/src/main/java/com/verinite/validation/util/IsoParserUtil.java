package com.verinite.validation.util;

import lombok.extern.slf4j.Slf4j;
import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.packager.GenericPackager;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class IsoParserUtil {

    /**
     * Parse ISO 8583 hex string into DE fields map
     *
     * @param hexMessage - raw hex string
     * @param packagerPath - classpath resource path
     * @return Map of DE number → value
     */
    public static Map<Integer, String> parse(
            String hexMessage, String packagerPath) throws ISOException {

        Map<Integer, String> fields = new HashMap<>();

        try {
            // Load packager from classpath
            InputStream is = IsoParserUtil.class
                    .getClassLoader()
                    .getResourceAsStream(packagerPath);

            if (is == null) {
                throw new ISOException(
                        "Packager file not found: " + packagerPath);
            }

            GenericPackager packager = new GenericPackager(is);

            // Convert hex to bytes
            byte[] bytes = hexToBytes(hexMessage);

            // Unpack
            ISOMsg msg = new ISOMsg();
            msg.setPackager(packager);
            msg.unpack(bytes);

            // Extract MTI
            fields.put(0, msg.getMTI());

            // Extract all DE fields
            for (int i = 1; i <= 128; i++) {
                if (msg.hasField(i)) {
                    fields.put(i, msg.getString(i));
                }
            }

        } catch (Exception e) {
            log.error("ISO parse error: {}", e.getMessage());
            throw new ISOException("Parse failed: " + e.getMessage());
        }

        return fields;
    }

    /**
     * Extract MTI from hex message
     */
    public static String extractMti(
            String hexMessage, String packagerPath) throws ISOException {
        Map<Integer, String> fields = parse(hexMessage, packagerPath);
        return fields.getOrDefault(0, "UNKNOWN");
    }

    /**
     * Convert hex string to byte array
     */
    private static byte[] hexToBytes(String hex) {
        String clean = hex.replaceAll("\\s+", "");
        int len = clean.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(
                    clean.charAt(i), 16) << 4)
                    + Character.digit(clean.charAt(i + 1), 16));
        }
        return data;
    }
}