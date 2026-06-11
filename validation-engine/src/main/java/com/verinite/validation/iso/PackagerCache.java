package com.verinite.validation.iso;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.jpos.iso.packager.GenericPackager;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Slf4j
public class PackagerCache {

    private final Cache<Long, GenericPackager> cache = Caffeine.newBuilder()
            .maximumSize(50)
            .expireAfterWrite(30, TimeUnit.MINUTES)
            .recordStats()
            .build();

    public GenericPackager get(Long formatId, String xmlContent) {
        return cache.get(formatId, id -> loadPackager(id, xmlContent));
    }

    public void evict(Long formatId) {
        cache.invalidate(formatId);
        log.info("PackagerCache evicted formatId={}", formatId);
    }

    public void evictAll() {
        cache.invalidateAll();
        log.info("PackagerCache evicted ALL");
    }

    private GenericPackager loadPackager(Long formatId, String xmlContent) {

        log.info("PackagerCache MISS — loading formatId={}", formatId);

        String cleanXml = preprocessXml(formatId, xmlContent);


        System.out.println("\n===== XML AFTER DOCTYPE REMOVAL =====");
        System.out.println(
                cleanXml.substring(0, Math.min(300, cleanXml.length()))
        );
        System.out.println("=====================================");

        byte[] xmlBytes = cleanXml.getBytes(StandardCharsets.UTF_8);

        try (InputStream is = new ByteArrayInputStream(xmlBytes)) {

            int pos = cleanXml.indexOf("IFA_CHAR");

            if (pos > 0) {
                int start = Math.max(0, pos - 200);
                int end = Math.min(cleanXml.length(), pos + 200);

                System.out.println(cleanXml.substring(start, end));
            }

            Pattern p = Pattern.compile("class=\"([^\"]+)\"");
            Matcher m = p.matcher(cleanXml);

            while (m.find()) {

                String clazz = m.group(1);

                try {
                    Class.forName(clazz);
                    System.out.println("FOUND CLASS = " + clazz);
                } catch (ClassNotFoundException ex) {
                    System.out.println("MISSING CLASS = " + clazz);
                }
            }
            GenericPackager packager = new GenericPackager(is);

            log.info("Packager loaded successfully");

            return packager;

        } catch (Exception e) {

            log.error("Packager error", e);

            throw new RuntimeException(
                    "Failed to load GenericPackager for formatId="
                            + formatId + ": " + e.getMessage(),
                    e
            );
        }
    }

    private String preprocessXml(Long formatId, String xmlContent) {

        if (xmlContent == null || xmlContent.isBlank()) {
            throw new RuntimeException(
                    "xmlContent is null or blank for formatId=" + formatId
            );
        }

        String s = xmlContent;

        // Remove UTF-8 BOM if present
        if (s.startsWith("\uFEFF")) {
            s = s.substring(1);
        }

        // Unescape quotes stored in DB
        s = s.replace("\\\"", "\"");

        // Normalize line endings
        s = s.replace("\\r\\n", "\n");
        s = s.replace("\\n", "\n");
        s = s.replace("\\r", "\n");

        s = s.replace("\r\n", "\n");
        s = s.replace("\r", "\n");

        // =====================================================
        // FIX LEGACY DTD URL
        // =====================================================
        s = s.replace(
                "http://jpos.org/dtd/genericpackager.dtd",
                "http://jpos.org/dtd/generic-packager-1.0.dtd"
        );

        s = s.replace(
                "\"genericpackager.dtd\"",
                "\"http://jpos.org/dtd/generic-packager-1.0.dtd\""
        );

// =====================================================
// FIX LEGACY jPOS PACKAGER CLASSES
// =====================================================

// DON'T map IFA_CHAR -> IFA_LCHAR
// IFA_LCHAR is variable-length and causes:
// Length 12 too long for org.jpos.iso.IFA_LCHAR

        s = s.replace(
                "org.jpos.iso.IFA_CHAR",
                "org.jpos.iso.IF_CHAR"
        );

// =====================================================

        // =====================================================

        s = s.strip();

        log.info("Contains DOCTYPE = {}", s.contains("<!DOCTYPE"));
        log.info("Contains DTD = {}", s.contains("generic-packager-1.0.dtd"));
        log.info("Contains IFA_CHAR = {}", s.contains("IFA_CHAR"));
        log.info("Contains IFA_LCHAR = {}", s.contains("IFA_LCHAR"));
        log.info("XML LENGTH = {}", s.length());

        System.out.println("\n===== FINAL XML START =====");
        System.out.println(
                s.substring(0, Math.min(1000, s.length()))
        );
        System.out.println("===== FINAL XML END =====");

        return s;
    }

    public long size() {
        return cache.estimatedSize();
    }
}