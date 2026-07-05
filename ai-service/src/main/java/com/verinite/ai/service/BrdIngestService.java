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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class BrdIngestService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "docx", "txt");
    private static final int CHUNK_SIZE = 800;

    private final BrdDocumentRepository        brdDocumentRepository;
    private final BrdEmbeddingChunkRepository  brdEmbeddingChunkRepository;
    private final BrdExtractionService         brdExtractionService;
    private final OllamaClient                 ollamaClient;
    private final ObjectMapper                 objectMapper;

    @Value("${brd.upload.dir:./brd-uploads}")
    private String uploadDir;

    public BrdDocument ingest(MultipartFile file, String uploadedBy) {
        validateFile(file);

        BrdDocument document = BrdDocument.builder()
                .originalFilename(file.getOriginalFilename())
                .contentType(file.getContentType())
                .status(BrdExtractStatus.PROCESSING)
                .uploadedBy(uploadedBy)
                .build();
        document = brdDocumentRepository.save(document);

        try {
            String storedPath = storeToDisk(file, document.getId());
            document.setStoredPath(storedPath);

            String fullText = extractText(file);

            List<String> chunks = chunkText(fullText);
            for (int i = 0; i < chunks.size(); i++) {
                String chunkText = chunks.get(i);
                List<Double> embedding = ollamaClient.getEmbedding(chunkText);
                BrdEmbeddingChunk chunk = BrdEmbeddingChunk.builder()
                        .brdDocumentId(document.getId())
                        .chunkIndex(i)
                        .chunkText(chunkText)
                        .embeddingVector(serializeEmbedding(embedding))
                        .profileId(null) // set later, on confirm
                        .build();
                brdEmbeddingChunkRepository.save(chunk);
            }

            BrdExtractedConfig config = brdExtractionService.extract(fullText);

            document.setExtractedJson(objectMapper.writeValueAsString(config));
            document.setStatus(BrdExtractStatus.COMPLETED);
            document.setErrorMessage(null);
            return brdDocumentRepository.save(document);

        } catch (Exception e) {
            log.error("[BRD] Ingestion failed for documentId={}: {}", document.getId(), e.getMessage(), e);
            document.setStatus(BrdExtractStatus.FAILED);
            document.setErrorMessage(truncate(e.getMessage(), 2000));
            return brdDocumentRepository.save(document);
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file is empty");
        }
        String filename = file.getOriginalFilename();
        String ext = (filename != null && filename.contains("."))
                ? filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT)
                : "";
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new IllegalArgumentException("Unsupported file type: ." + ext + " — only PDF, DOCX, TXT are supported");
        }
    }

    private String storeToDisk(MultipartFile file, Long documentId) throws IOException {
        Path dir = Paths.get(uploadDir);
        Files.createDirectories(dir);

        String safeName = documentId + "_" + UUID.randomUUID() + "_" + sanitize(file.getOriginalFilename());
        Path target = dir.resolve(safeName);
        Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        return target.toString();
    }

    private String sanitize(String filename) {
        return filename == null ? "upload" : filename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private String extractText(MultipartFile file) throws IOException {
        Tika tika = new Tika();
        tika.setMaxStringLength(-1); // no truncation — BRDs can be long
        try (InputStream is = file.getInputStream()) {
            return tika.parseToString(is, new Metadata());
        } catch (TikaException e) {
            throw new RuntimeException(e);
        }
    }

    /** ~800 char chunks, preferring paragraph boundaries; falls back to fixed-size slicing. */
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
                // Paragraph itself longer than CHUNK_SIZE — fixed-size slice it.
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