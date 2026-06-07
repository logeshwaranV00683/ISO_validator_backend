package com.verinite.validation.util;

import com.verinite.validation.iso.ParsedMessage;
import lombok.extern.slf4j.Slf4j;
import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.ISOPackager;
import org.jpos.iso.packager.GenericPackager;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class IsoParserUtil {

    /**
     * Parse using a pre-loaded ISOPackager (used in production — packager is cached).
     */
    public static ParsedMessage parse(String hexMessage, ISOPackager packager)
            throws ISOException {
        try {
            byte[] bytes = hexToBytes(hexMessage);
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
     * Parse using a classpath resource path (used in unit tests).
     */
    public static ParsedMessage parse(String hexMessage, String packagerPath)
            throws ISOException {
        try {
            InputStream is = IsoParserUtil.class
                    .getClassLoader()
                    .getResourceAsStream(packagerPath);
            if (is == null) {
                throw new ISOException("Packager file not found: " + packagerPath);
            }
            return parse(hexMessage, new GenericPackager(is));
        } catch (ISOException e) {
            throw e;
        } catch (Exception e) {
            throw new ISOException("Parse failed: " + e.getMessage(), e);
        }
    }

    /** Build a jPOS ISOMsg for a given packager and DE fields (used by MessageBuilderService). */
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

    /** Convert hex string to byte array. */
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