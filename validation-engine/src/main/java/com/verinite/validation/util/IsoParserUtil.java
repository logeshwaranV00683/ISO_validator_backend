package com.verinite.validation.util;

import com.verinite.validation.iso.ParsedMessage;
import lombok.extern.slf4j.Slf4j;
import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.ISOPackager;
import org.jpos.iso.packager.GenericPackager;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class IsoParserUtil {

    /**
     * Parse a message — accepts BOTH hex-encoded strings and raw ASCII/binary strings.
     * Auto-detects format via {@link #toBytes(String)}.
     */
    public static ParsedMessage parse(String message, ISOPackager packager)
            throws ISOException {
        try {
            byte[] bytes = toBytes(message);          // ← was hexToBytes(); now auto-detects
            ISOMsg msg   = new ISOMsg();
            msg.setPackager(packager);
            msg.unpack(bytes);

            Map<Integer, String> fields = new HashMap<>();
            fields.put(0, msg.getMTI());
            for (int i = 1; i <= 128; i++) {
                if (msg.hasField(i)) {
                    fields.put(i, msg.getString(i));
                }
            }
            return new ParsedMessage(msg.getMTI(), fields);
        } catch (ISOException e) {
            throw e;
        } catch (Exception e) {
            throw new ISOException("Parse failed: " + e.getMessage(), e);
        }
    }

    /**
     * Parse using a classpath resource path (unit tests).
     */
    public static ParsedMessage parse(String message, String packagerPath)
            throws ISOException {
        try {
            InputStream is = IsoParserUtil.class
                    .getClassLoader()
                    .getResourceAsStream(packagerPath);
            if (is == null) {
                throw new ISOException("Packager file not found: " + packagerPath);
            }
            return parse(message, new GenericPackager(is));
        } catch (ISOException e) {
            throw e;
        } catch (Exception e) {
            throw new ISOException("Parse failed: " + e.getMessage(), e);
        }
    }

    /** Build a jPOS ISOMsg (used by MessageBuilderService). */
    public static ISOMsg buildMsg(String mti,
                                  Map<Integer, String> fields,
                                  ISOPackager packager) throws ISOException {
        ISOMsg msg = new ISOMsg();
        msg.setPackager(packager);
        msg.setMTI(mti);
        for (Map.Entry<Integer, String> entry : fields.entrySet()) {
            msg.set(entry.getKey(), entry.getValue());
        }
        return msg;
    }

    /**
     * Smart byte converter — auto-detects input format:
     * <ul>
     *   <li>All chars are valid hex digits (0-9, a-f, A-F) AND even length
     *       → hex-encoded binary → {@link #hexToBytes(String)}</li>
     *   <li>Otherwise → raw wire-format message string → ISO-8859-1 bytes</li>
     * </ul>
     *
     * Edge case handled: a raw ISO 8583 ASCII message that happens to look like
     * hex (e.g., "0200B220...") will contain non-hex chars (B, spaces, etc.)
     * and fall through to the raw path correctly.
     *
     * FIX: previously did `message.replaceAll("\\s+", "")` on the WHOLE input
     * before branching. That strips internal spaces too — and raw ISO 8583
     * messages legitimately contain space-padding inside fixed-length fields
     * (DE43 "TEST MERCHANT/CHENNAI       IN          ", DE94 "654321 ", DE98
     * "VERINITESETTLE001        ", DE95). Deleting those bytes shifts every
     * field after them, which is what produced
     * "IFA_LLNUM: ... Expected digit ... unpacking field=100".
     * Now: only leading/trailing whitespace is trimmed for the raw path.
     * Whitespace is only fully collapsed when deciding/performing hex
     * decoding, where it's just a separator between byte-pairs and carries
     * no data of its own.
     */
    public static byte[] toBytes(String message) {
        if (message == null) throw new IllegalArgumentException("Message cannot be null");

        String trimmed = message.strip();

        String hexCandidate = trimmed.replaceAll("\\s+", "");
        if (isHexEncoded(hexCandidate)) {
            log.debug("Input detected as hex-encoded ({} chars → {} bytes)",
                    hexCandidate.length(), hexCandidate.length() / 2);
            return hexToBytes(hexCandidate);
        }

        log.debug("Input detected as raw message string ({} chars)", trimmed.length());
        return trimmed.getBytes(StandardCharsets.ISO_8859_1);
    }

    /**
     * Returns true only when every character is a hex digit AND length is even.
     * Empty / null / odd-length strings → false.
     */
    private static boolean isHexEncoded(String s) {
        if (s == null || s.isEmpty() || s.length() % 2 != 0) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean hex = (c >= '0' && c <= '9')
                    || (c >= 'a' && c <= 'f')
                    || (c >= 'A' && c <= 'F');
            if (!hex) return false;
        }
        return true;
    }

    /** Convert a pre-cleaned hex string to bytes. */
    public static byte[] hexToBytes(String hex) {
        String clean = hex.replaceAll("\\s+", "");
        int    len   = clean.length();
        byte[] data  = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(clean.charAt(i), 16) << 4)
                    + Character.digit(clean.charAt(i + 1), 16));
        }
        return data;
    }
}