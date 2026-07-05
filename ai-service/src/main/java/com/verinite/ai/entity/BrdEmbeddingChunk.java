package com.verinite.ai.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "brd_embedding_chunks")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BrdEmbeddingChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "brd_document_id", nullable = false)
    private Long brdDocumentId;

    @Column(name = "chunk_index", nullable = false)
    private Integer chunkIndex;

    @Column(name = "chunk_text", columnDefinition = "TEXT", nullable = false)
    private String chunkText;

    /** JSON-serialized float array, e.g. "[0.0123, -0.045, ...]" */
    @Column(name = "embedding_vector", columnDefinition = "LONGTEXT")
    private String embeddingVector;

    /** Null until the parent BRD is confirmed into a switch profile. */
    @Column(name = "profile_id")
    private Long profileId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}