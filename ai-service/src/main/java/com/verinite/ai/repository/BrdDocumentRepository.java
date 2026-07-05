package com.verinite.ai.repository;

import com.verinite.ai.entity.BrdDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BrdDocumentRepository extends JpaRepository<BrdDocument, Long> {

    List<BrdDocument> findByUploadedByOrderByCreatedAtDesc(String uploadedBy);

    List<BrdDocument> findAllByOrderByCreatedAtDesc();
}