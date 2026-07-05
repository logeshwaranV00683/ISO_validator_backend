-- ================================================================
-- V1__create_brd_tables.sql
-- BRD AI Feature: BRD document ingestion + embedding storage.
-- ================================================================

-- The `scope` column is a MySQL ENUM, not a free-text column.
-- TemplateScope.BRD_PARSE must be added at the DB level too, or
-- inserts/updates using it will fail with "Data truncated for column 'scope'".
ALTER TABLE ai_prompt_templates
    MODIFY COLUMN scope ENUM('GLOBAL','PROFILE','BRD_PARSE') NOT NULL DEFAULT 'GLOBAL';

CREATE TABLE brd_documents (
                               id                BIGINT PRIMARY KEY AUTO_INCREMENT,
                               original_filename VARCHAR(255) NOT NULL,
                               content_type      VARCHAR(100),
                               stored_path       VARCHAR(500),
                               status            ENUM('PENDING','PROCESSING','COMPLETED','FAILED','CONFIRMED') NOT NULL DEFAULT 'PENDING',
                               extracted_json    LONGTEXT,
                               error_message     TEXT,
                               uploaded_by       VARCHAR(50),
                               confirmed_by      VARCHAR(50),
                               confirmed_at      DATETIME,
                               created_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                               updated_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

                               KEY idx_brd_documents_status      (status),
                               KEY idx_brd_documents_uploaded_by (uploaded_by)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Uploaded BRD documents and their AI-extracted switch configuration.';

CREATE TABLE brd_embedding_chunks (
                                      id               BIGINT PRIMARY KEY AUTO_INCREMENT,
                                      brd_document_id  BIGINT NOT NULL,
                                      chunk_index      INT NOT NULL,
                                      chunk_text       TEXT NOT NULL,
                                      embedding_vector LONGTEXT,
                                      profile_id       BIGINT,
                                      created_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                      KEY idx_brd_chunks_document_id (brd_document_id),
                                      KEY idx_brd_chunks_profile_id  (profile_id),
                                      CONSTRAINT fk_brd_chunks_document
                                          FOREIGN KEY (brd_document_id) REFERENCES brd_documents(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Chunk-level embeddings of confirmed BRDs — used for switch-profile suggestion.';

INSERT INTO ai_prompt_templates (template_name, scope, prompt_template, variables_used, active, created_by, created_at, updated_at)
VALUES (
           'BRD Parser',
           'BRD_PARSE',
           'You are an ISO 8583 payment message expert.\n\nAnalyze the following BRD document and extract switch configuration.\n\nBRD Content:\n{brd_text}\n\nReturn ONLY a valid JSON object with this exact structure, no preamble, no markdown:\n{\n  "switchProfile": {\n    "profileName": "...",\n    "description": "...",\n    "environment": "DEV"\n  },\n  "mti": "0200",\n  "fieldDefinitions": [\n    {\n      "deNumber": "DE2",\n      "fieldName": "Primary Account Number",\n      "dataType": "numeric",\n      "maxLength": 19,\n      "isMandatory": true,\n      "isLlvar": true,\n      "isLllvar": false\n    }\n  ],\n  "rules": [\n    {\n      "deNumber": "DE2",\n      "fieldName": "Primary Account Number",\n      "isMandatory": true,\n      "severity": "CRITICAL",\n      "maxLength": 19,\n      "dataType": "numeric"\n    }\n  ],\n  "confidence": 0.85,\n  "warnings": []\n}',
           '{brd_text}',
           true,
           'system',
           NOW(),
           NOW()
       );