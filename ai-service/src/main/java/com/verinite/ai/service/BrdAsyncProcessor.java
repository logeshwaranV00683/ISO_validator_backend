//package com.verinite.ai.service;
//
//import com.fasterxml.jackson.databind.ObjectMapper;
//import com.verinite.ai.client.OllamaClient;
//import com.verinite.ai.dto.BrdExtractedConfig;
//import com.verinite.ai.entity.BrdDocument;
//import com.verinite.ai.entity.BrdEmbeddingChunk;
//import com.verinite.ai.repository.BrdDocumentRepository;
//import com.verinite.ai.repository.BrdEmbeddingChunkRepository;
//import com.verinite.common.enums.BrdExtractStatus;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.apache.tika.Tika;
//import org.apache.tika.exception.TikaException;
//import org.apache.tika.metadata.Metadata;
//import org.springframework.scheduling.annotation.Async;
//import org.springframework.stereotype.Service;
//
//import java.io.ByteArrayInputStream;
//import java.io.IOException;
//import java.io.InputStream;
//import java.util.ArrayList;
//import java.util.List;
//import java.util.concurrent.ExecutorService;
//import java.util.concurrent.Executors;
//import java.util.concurrent.Future;
//
///**
// * Holds the actual heavy-lifting BRD processing logic (text extraction,
// * embeddings, LLM extraction) as its own Spring bean.
// *
// * IMPORTANT: this must live in a separate bean from BrdIngestService.
// * @Async only works through Spring's proxy, which only intercepts calls
// * coming from OUTSIDE the bean. A same-class ("this.processAsync(...)")
// * call bypasses the proxy entirely and runs synchronously, silently
// * defeating the whole point of making this async. That self-invocation
// * bug is why the previous version showed no speed improvement — this
// * class exists specifically to avoid it.
// */
//@Service
//@RequiredArgsConstructor
//@Slf4j
//public class BrdAsyncProcessor {
//
//    // Larger chunks = far fewer Ollama embedding calls per document.
//    // 800 was needlessly small and multiplied the number of round trips.
//    private static final int CHUNK_SIZE = 2000;
//
//    // Bounded pool for embedding calls: enough to overlap network/model
//    // latency without hammering a local Ollama instance with unbounded
//    // concurrent requests. 4 is a safe default for a single-GPU/CPU
//    // Ollama box; raise it only if you know Ollama can handle more.
//    private static final int EMBEDDING_CONCURRENCY = 4;
//
//    private final BrdDocumentRepository        brdDocumentRepository;
//    private final BrdEmbeddingChunkRepository  brdEmbeddingChunkRepository;
//    private final BrdExtractionService         brdExtractionService;
//    private final OllamaClient                 ollamaClient;
//    private final ObjectMapper                 objectMapper;
//
//    @Async
//    public void processAsync(Long documentId, byte[] fileBytes, String contentType) {
//        BrdDocument document = brdDocumentRepository.findById(documentId).orElse(null);
//        if (document == null) {
//            log.error("[BRD] processAsync: documentId={} not found", documentId);
//            return;
//        }
//
//        long start = System.currentTimeMillis();
//        try {
//            String fullText = extractText(fileBytes, contentType);
//            log.info("[BRD] documentId={} text extracted, length={}, elapsedMs={}",
//                    documentId, fullText.length(), System.currentTimeMillis() - start);
//
//            List<String> chunks = chunkText(fullText);
//            log.info("[BRD] documentId={} chunked into {} pieces", documentId, chunks.size());
//
//            List<BrdEmbeddingChunk> chunkEntities = embedChunksInParallel(documentId, chunks);
//            brdEmbeddingChunkRepository.saveAll(chunkEntities);
//            log.info("[BRD] documentId={} embeddings done, elapsedMs={}",
//                    documentId, System.currentTimeMillis() - start);
//
//            BrdExtractedConfig config = brdExtractionService.extract(fullText);
//            log.info("[BRD] documentId={} LLM extraction done, elapsedMs={}",
//                    documentId, System.currentTimeMillis() - start);
//
//            document.setExtractedJson(objectMapper.writeValueAsString(config));
//            document.setStatus(BrdExtractStatus.COMPLETED);
//            document.setErrorMessage(null);
//            brdDocumentRepository.save(document);
//
//            log.info("[BRD] documentId={} COMPLETED totalElapsedMs={}",
//                    documentId, System.currentTimeMillis() - start);
//
//        } catch (Exception e) {
//            log.error("[BRD] Ingestion failed for documentId={}: {}", documentId, e.getMessage(), e);
//            document.setStatus(BrdExtractStatus.FAILED);
//            document.setErrorMessage(truncate(e.getMessage(), 2000));
//            brdDocumentRepository.save(document);
//        }
//    }
//
//    private List<BrdEmbeddingChunk> embedChunksInParallel(Long documentId, List<String> chunks) {
//        if (chunks.isEmpty()) return new ArrayList<>();
//
//        int poolSize = Math.min(EMBEDDING_CONCURRENCY, chunks.size());
//        ExecutorService executor = Executors.newFixedThreadPool(poolSize);
//        try {
//            List<Future<List<Double>>> futures = new ArrayList<>(chunks.size());
//            for (String chunkText : chunks) {
//                futures.add(executor.submit(() -> ollamaClient.getEmbedding(chunkText)));
//            }
//
//            List<BrdEmbeddingChunk> result = new ArrayList<>(chunks.size());
//            for (int i = 0; i < chunks.size(); i++) {
//                List<Double> embedding;
//                try {
//                    embedding = futures.get(i).get();
//                } catch (Exception e) {
//                    log.warn("[BRD] Embedding failed for chunk {} of documentId={}: {}", i, documentId, e.getMessage());
//                    embedding = List.of();
//                }
//                result.add(BrdEmbeddingChunk.builder()
//                        .brdDocumentId(documentId)
//                        .chunkIndex(i)
//                        .chunkText(chunks.get(i))
//                        .embeddingVector(serializeEmbedding(embedding))
//                        .profileId(null) // set later, on confirm
//                        .build());
//            }
//            return result;
//        } finally {
//            executor.shutdown();
//        }
//    }
//
//    private String extractText(byte[] fileBytes, String contentType) throws IOException {
//        Tika tika = new Tika();
//        tika.setMaxStringLength(-1); // no truncation — BRDs can be long
//        try (InputStream is = new ByteArrayInputStream(fileBytes)) {
//            Metadata metadata = new Metadata();
//            if (contentType != null) {
//                metadata.set(Metadata.CONTENT_TYPE, contentType);
//            }
//            return tika.parseToString(is, metadata);
//        } catch (TikaException e) {
//            throw new RuntimeException(e);
//        }
//    }
//
//    /** ~2000 char chunks, preferring paragraph boundaries; falls back to fixed-size slicing. */
//    private List<String> chunkText(String text) {
//        List<String> chunks = new ArrayList<>();
//        if (text == null || text.isBlank()) return chunks;
//
//        String[] paragraphs = text.split("\\r?\\n\\s*\\r?\\n");
//        StringBuilder current = new StringBuilder();
//
//        for (String para : paragraphs) {
//            String trimmed = para.trim();
//            if (trimmed.isEmpty()) continue;
//
//            if (current.length() + trimmed.length() + 1 > CHUNK_SIZE) {
//                if (current.length() > 0) {
//                    chunks.add(current.toString().trim());
//                    current.setLength(0);
//                }
//                if (trimmed.length() > CHUNK_SIZE) {
//                    for (int i = 0; i < trimmed.length(); i += CHUNK_SIZE) {
//                        chunks.add(trimmed.substring(i, Math.min(i + CHUNK_SIZE, trimmed.length())));
//                    }
//                    continue;
//                }
//            }
//            if (current.length() > 0) current.append("\n\n");
//            current.append(trimmed);
//        }
//        if (current.length() > 0) chunks.add(current.toString().trim());
//
//        return chunks;
//    }
//
//    private String serializeEmbedding(List<Double> embedding) {
//        try {
//            return objectMapper.writeValueAsString(embedding);
//        } catch (Exception e) {
//            return "[]";
//        }
//    }
//
//    private String truncate(String s, int max) {
//        if (s == null) return "Unknown error";
//        return s.length() <= max ? s : s.substring(0, max);
//    }
//}
package com.verinite.ai.service;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.verinite.ai.client.OllamaClient;
import com.verinite.ai.dto.BrdExtractedConfig;
import com.verinite.ai.entity.BrdDocument;
import com.verinite.ai.entity.BrdEmbeddingChunk;
import com.verinite.ai.repository.BrdDocumentRepository;
import com.verinite.ai.repository.BrdEmbeddingChunkRepository;
import com.verinite.common.enums.BrdExtractStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class BrdAsyncProcessor {

    private static final int CHUNK_SIZE = 2000;
    private static final int EMBEDDING_CONCURRENCY = 4;

    private static final long ESTIMATED_LLM_DURATION_MS = 5 * 60 * 1000L;
    private final BrdDocumentRepository        brdDocumentRepository;
    private final BrdEmbeddingChunkRepository  brdEmbeddingChunkRepository;
    private final BrdExtractionService         brdExtractionService;
    private final OllamaClient                 ollamaClient;
    private final ObjectMapper                 objectMapper;

    @Async
    public void processAsync(Long documentId, byte[] fileBytes, String contentType) {
        BrdDocument document = brdDocumentRepository.findById(documentId).orElse(null);
        if (document == null) {
            log.error("[BRD] processAsync: documentId={} not found", documentId);
            return;
        }

        long start = System.currentTimeMillis();
        try {
            updateProgress(documentId, 5);
            String fullText = extractText(fileBytes, contentType);
            log.info("[BRD] documentId={} text extracted, length={}, elapsedMs={}",
                    documentId, fullText.length(), System.currentTimeMillis() - start);

            List<String> chunks = chunkText(fullText);
            log.info("[BRD] documentId={} chunked into {} pieces", documentId, chunks.size());
            updateProgress(documentId, 15);

            List<BrdEmbeddingChunk> chunkEntities = embedChunksInParallel(documentId, chunks);
            brdEmbeddingChunkRepository.saveAll(chunkEntities);
            log.info("[BRD] documentId={} embeddings done, elapsedMs={}",
                    documentId, System.currentTimeMillis() - start);
            updateProgress(documentId, 30);

            ScheduledExecutorService ticker = Executors.newSingleThreadScheduledExecutor();
            long llmStart = System.currentTimeMillis();
            ticker.scheduleAtFixedRate(() -> {
                long elapsed = System.currentTimeMillis() - llmStart;
                int pct = 30 + (int) Math.min(65, (elapsed * 65) / ESTIMATED_LLM_DURATION_MS);
                updateProgress(documentId, pct);
            }, 2, 2, TimeUnit.SECONDS);

            BrdExtractedConfig config;
            try {
                config = brdExtractionService.extract(fullText);
            } finally {
                ticker.shutdownNow();
            }
            log.info("[BRD] documentId={} LLM extraction done, elapsedMs={}",
                    documentId, System.currentTimeMillis() - start);
            updateProgress(documentId, 95);

            document.setExtractedJson(objectMapper.writeValueAsString(config));
            document.setStatus(BrdExtractStatus.COMPLETED);
            document.setErrorMessage(null);
            document.setProgressPercent(100);
            brdDocumentRepository.save(document);

            log.info("[BRD] documentId={} COMPLETED totalElapsedMs={}",
                    documentId, System.currentTimeMillis() - start);

        } catch (Exception e) {
            log.error("[BRD] Ingestion failed for documentId={}: {}", documentId, e.getMessage(), e);
            document.setStatus(BrdExtractStatus.FAILED);
            document.setErrorMessage(truncate(e.getMessage(), 2000));
            brdDocumentRepository.save(document);
        }
    }

    private void updateProgress(Long documentId, int percent) {
        try {
            brdDocumentRepository.findById(documentId).ifPresent(d -> {
                d.setProgressPercent(percent);
                brdDocumentRepository.save(d);
            });
        } catch (Exception e) {

            log.warn("[BRD] Failed to update progress for documentId={}: {}", documentId, e.getMessage());
        }
    }
    private List<BrdEmbeddingChunk> embedChunksInParallel(Long documentId, List<String> chunks) {
        if (chunks.isEmpty()) return new ArrayList<>();

        int poolSize = Math.min(EMBEDDING_CONCURRENCY, chunks.size());
        ExecutorService executor = Executors.newFixedThreadPool(poolSize);
        try {
            List<Future<List<Double>>> futures = new ArrayList<>(chunks.size());
            for (String chunkText : chunks) {
                futures.add(executor.submit(() -> ollamaClient.getEmbedding(chunkText)));
            }

            List<BrdEmbeddingChunk> result = new ArrayList<>(chunks.size());
            for (int i = 0; i < chunks.size(); i++) {
                List<Double> embedding;
                try {
                    embedding = futures.get(i).get();
                } catch (Exception e) {
                    log.warn("[BRD] Embedding failed for chunk {} of documentId={}: {}", i, documentId, e.getMessage());
                    embedding = List.of();
                }
                result.add(BrdEmbeddingChunk.builder()
                        .brdDocumentId(documentId)
                        .chunkIndex(i)
                        .chunkText(chunks.get(i))
                        .embeddingVector(serializeEmbedding(embedding))
                        .profileId(null)
                        .build());
            }
            return result;
        } finally {
            executor.shutdown();
        }
    }

    private String extractText(byte[] fileBytes, String contentType) throws IOException {
        Tika tika = new Tika();
        tika.setMaxStringLength(-1);
        try (InputStream is = new ByteArrayInputStream(fileBytes)) {
            Metadata metadata = new Metadata();
            if (contentType != null) {
                metadata.set(Metadata.CONTENT_TYPE, contentType);
            }
            return tika.parseToString(is, metadata);
        } catch (TikaException e) {
            throw new RuntimeException(e);
        }
    }

    private List<String> chunkText(String text) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isBlank()) return chunks;

        String[] paragraphs = text.split("\\r?\\n\\s*\\r?\\n");
        StringBuilder current = new StringBuilder();

        for (String para : paragraphs) {
            String trimmed = para.trim();
            if (trimmed.isEmpty()) continue;

            if (current.length() + trimmed.length() + 1 > CHUNK_SIZE) {
                if (current.length() > 0) {
                    chunks.add(current.toString().trim());
                    current.setLength(0);
                }
                if (trimmed.length() > CHUNK_SIZE) {
                    for (int i = 0; i < trimmed.length(); i += CHUNK_SIZE) {
                        chunks.add(trimmed.substring(i, Math.min(i + CHUNK_SIZE, trimmed.length())));
                    }
                    continue;
                }
            }
            if (current.length() > 0) current.append("\n\n");
            current.append(trimmed);
        }
        if (current.length() > 0) chunks.add(current.toString().trim());

        return chunks;
    }

    private String serializeEmbedding(List<Double> embedding) {
        try {
            return objectMapper.writeValueAsString(embedding);
        } catch (Exception e) {
            return "[]";
        }
    }

    private String truncate(String s, int max) {
        if (s == null) return "Unknown error";
        return s.length() <= max ? s : s.substring(0, max);
    }
}