package com.verinite.ai.repository;

import com.verinite.ai.entity.BrdEmbeddingChunk;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BrdEmbeddingChunkRepository extends JpaRepository<BrdEmbeddingChunk, Long> {

    List<BrdEmbeddingChunk> findByBrdDocumentId(Long brdDocumentId);

    List<BrdEmbeddingChunk> findByProfileIdIsNotNull();
}