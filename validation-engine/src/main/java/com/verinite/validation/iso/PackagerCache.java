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

@Component
@Slf4j
public class PackagerCache {

    // key = formatId, value = loaded packager
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

    // ── private ───────────────────────────────────────────────────────────────

    private GenericPackager loadPackager(Long formatId, String xmlContent) {
        log.info("PackagerCache MISS — loading formatId={}", formatId);

        String cleanXml = preprocessXml(formatId, xmlContent);
        byte[] xmlBytes = cleanXml.getBytes(StandardCharsets.UTF_8);

        try (InputStream is = new ByteArrayInputStream(xmlBytes)) {
            GenericPackager packager = new GenericPackager(is);
            int maxField = packager.getFieldPackager(0).getLength()-1;

            log.info("PackagerCache loaded formatId={} maxField={}",
                    formatId, maxField);
            return packager;

        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to load GenericPackager for formatId=" + formatId
                            + ": " + e.getMessage(), e);
        }
    }

    /**
     * Sanitise the XML string before feeding it to jPOS GenericPackager.
     * Common issues from DB-stored XML:
     *  1. Leading/trailing whitespace or BOM character
     *  2. Windows CRLF line endings causing SAX column-count drift
     *  3. Missing XML declaration — jPOS doesn't require it but some parsers
     *     emit "no grammar found" when it's absent and XML is in validating mode
     */
    private String preprocessXml(Long formatId, String xmlContent) {
        if (xmlContent == null || xmlContent.isBlank()) {
            throw new RuntimeException("xmlContent is null or blank for formatId=" + formatId);
        }

        // Strip UTF-8 BOM
        String s = xmlContent.startsWith("\uFEFF")
                ? xmlContent.substring(1)
                : xmlContent;

        s = s.strip();

        // Normalise line endings
        s = s.replace("\r\n", "\n").replace("\r", "\n");

        // Ensure XML declaration is present — some SAX parsers behave better with it
        if (!s.startsWith("<?xml")) {
            s = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + s;
        }

        log.debug("PackagerCache preprocessed XML for formatId={} length={}", formatId, s.length());
        return s;
    }

    public long size() {
        return cache.estimatedSize();
    }
}