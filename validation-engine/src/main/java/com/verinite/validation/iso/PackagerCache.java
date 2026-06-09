package com.verinite.validation.iso;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.jpos.iso.ISOPackager;
import org.jpos.iso.packager.GenericPackager;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

@Component
@Slf4j
public class PackagerCache {

    // DOCTYPE causes SAXParseException when the external DTD URL can't be fetched
    private static final Pattern DOCTYPE_PATTERN =
            Pattern.compile("(?s)<!DOCTYPE[^\\[>]*(?:\\[[^\\]]*])?\\s*>\\s*");

    private final Cache<Long, ISOPackager> cache =
            Caffeine.newBuilder()
                    .maximumSize(50)
                    .build();

    public ISOPackager get(Long formatId, String xmlContent) {
        return cache.get(formatId, id -> {
            log.info("PackagerCache MISS — loading formatId={}", formatId);
            try {
                String cleanXml = stripDoctype(xmlContent);
                byte[] xmlBytes = cleanXml.getBytes(StandardCharsets.UTF_8);
                return new GenericPackager(new ByteArrayInputStream(xmlBytes));
            } catch (Exception e) {
                throw new RuntimeException(
                        "Failed to load GenericPackager for formatId=" + formatId
                                + ": " + e.getMessage(), e);
            }
        });
    }

    public void evict(Long formatId) {
        cache.invalidate(formatId);
        log.info("PackagerCache evicted formatId={}", formatId);
    }

    public void evictAll() {
        cache.invalidateAll();
        log.info("PackagerCache evicted ALL entries");
    }

    public long size() {
        return cache.estimatedSize();
    }

    /** Strip DOCTYPE declaration so the SAX parser never tries to resolve the external DTD URL. */
    private String stripDoctype(String xmlContent) {
        String result = DOCTYPE_PATTERN.matcher(xmlContent).replaceAll("").trim();
        if (!result.startsWith("<?xml") && !result.startsWith("<isopackager")) {
            // Make sure the XML declaration is still there if it was there before
            if (xmlContent.startsWith("<?xml")) {
                int firstTag = xmlContent.indexOf("<?xml");
                int endOfDecl = xmlContent.indexOf("?>", firstTag) + 2;
                String xmlDecl = xmlContent.substring(firstTag, endOfDecl);
                if (!result.startsWith("<?xml")) {
                    result = xmlDecl + "\n" + result;
                }
            }
        }
        return result;
    }
}