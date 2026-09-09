


-- ================================================================
-- V2__tighten_brd_parse_prompt.sql
-- BRD AI Feature: fix "field definition leaking into generated XML" bug.
--
-- Root cause: the local Ollama model sometimes ignored the schema and
-- stuffed the whole field definition (type/length/mandatory/LLVAR flags)
-- into "fieldName", e.g. "Primary Account Number, numeric, LLVAR, max 19,
-- mandatory". That string flowed straight through BrdConfirmService into
-- the generated packager XML's name="..." attribute.
--
-- Code-side sanitization now guards against this in BrdExtractionService
-- and BrdConfirmService regardless of what the model returns. This
-- migration additionally tightens the prompt so it happens less often,
-- with an explicit rule + a right/wrong example for fieldName.
-- ================================================================

UPDATE ai_prompt_templates
SET prompt_template = 'You are an ISO 8583 payment message expert.\n\nAnalyze the following BRD document and extract switch configuration.\n\nBRD Content:\n{brd_text}\n\nSTRICT RULE for "fieldName": it must be ONLY the short human-readable label of the data element - never include the data type, max length, mandatory/optional status, or LLVAR/LLLVAR flags in this field. Those belong in their own separate JSON properties (dataType, maxLength, isMandatory, isLlvar, isLllvar), not inside fieldName.\n\nCorrect example: "fieldName": "Primary Account Number"\nWrong example (do NOT do this): "fieldName": "Primary Account Number, numeric, LLVAR, max 19, mandatory"\n\nReturn ONLY a valid JSON object with this exact structure, no preamble, no markdown:\n{\n  "switchProfile": {\n    "profileName": "...",\n    "description": "...",\n    "environment": "DEV"\n  },\n  "mti": "0200",\n  "fieldDefinitions": [\n    {\n      "deNumber": "DE2",\n      "fieldName": "Primary Account Number",\n      "dataType": "numeric",\n      "maxLength": 19,\n      "isMandatory": true,\n      "isLlvar": true,\n      "isLllvar": false\n    }\n  ],\n  "rules": [\n    {\n      "deNumber": "DE2",\n      "fieldName": "Primary Account Number",\n      "isMandatory": true,\n      "severity": "CRITICAL",\n      "maxLength": 19,\n      "dataType": "numeric"\n    }\n  ],\n  "confidence": 0.85,\n  "warnings": []\n}',
    updated_at = NOW()
WHERE scope = 'BRD_PARSE'
  AND template_name = 'BRD Parser'
  AND active = true
  AND deleted_at IS NULL;