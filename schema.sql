-- =============================================================
--  ISO VALIDATOR — SINGLE-DB SCHEMA
--  Database : iso_validator_db   (all lowercase — Linux safe)
--  Engine   : InnoDB  |  Charset: utf8mb4_unicode_ci
--  Tables   : 17
-- =============================================================

-- ------------------------------------------------------------
-- NAMING CONVENTIONS (read before editing)
-- ------------------------------------------------------------
--  database  : iso_validator_db
--  tables    : snake_case plural          e.g. switch_profiles
--  columns   : snake_case                 e.g. profile_name
--  PK        : always named "id"          BIGINT AUTO_INCREMENT
--  FK col    : {parent_singular}_id       e.g. profile_id
--  UK        : uk_{table}_{col(s)}
--  Index     : idx_{table}_{col(s)}
--  FK const  : fk_{child_table}_{col}
--  CHK const : chk_{table}_{rule}
--  Soft-del  : deleted_at DATETIME NULL  (NULL = alive)
--  Audit     : created_at / updated_at / created_by / updated_by
-- ------------------------------------------------------------

SET NAMES utf8mb4;
SET time_zone              = '+00:00';
SET foreign_key_checks     = 0;
SET sql_mode               = 'STRICT_TRANS_TABLES,NO_ZERO_DATE,NO_ZERO_IN_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION';

-- =============================================================
-- DATABASE
-- =============================================================
CREATE DATABASE IF NOT EXISTS iso_validator_db
    CHARACTER SET  utf8mb4
    COLLATE        utf8mb4_unicode_ci;

USE iso_validator_db;

-- =============================================================
-- DROP ORDER  (child → parent to satisfy FK constraints)
-- =============================================================
DROP TABLE IF EXISTS audit_logs;
DROP TABLE IF EXISTS validation_run_errors;
DROP TABLE IF EXISTS validation_run_fields;
DROP TABLE IF EXISTS validation_runs;
DROP TABLE IF EXISTS ai_run_logs;
DROP TABLE IF EXISTS ai_prompt_template_versions;
DROP TABLE IF EXISTS ai_prompt_templates;
DROP TABLE IF EXISTS ollama_config;
DROP TABLE IF EXISTS field_definitions;
DROP TABLE IF EXISTS rule_allowed_values;
DROP TABLE IF EXISTS validation_rules;
DROP TABLE IF EXISTS message_format_versions;
DROP TABLE IF EXISTS message_formats;
DROP TABLE IF EXISTS switch_profiles;
DROP TABLE IF EXISTS system_config;
DROP TABLE IF EXISTS user_sessions;
DROP TABLE IF EXISTS users;

-- =============================================================
-- 1. users
-- =============================================================
CREATE TABLE users (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,
    username            VARCHAR(50)     NOT NULL,
    password_hash       VARCHAR(255)    NOT NULL    COMMENT 'BCrypt — NEVER store plaintext',
    full_name           VARCHAR(100)    NOT NULL,
    email               VARCHAR(150)    NULL,
    avatar_initials     VARCHAR(5)      NULL        COMMENT '2-char derived from full_name e.g. JD',
    role                ENUM('ADMIN','ANALYST','VIEWER') NOT NULL DEFAULT 'VIEWER',
    active              BOOLEAN         NOT NULL DEFAULT TRUE,
    failed_login_count  INT             NOT NULL DEFAULT 0,
    locked_until        DATETIME        NULL        COMMENT 'NULL = not locked',
    last_login_at       DATETIME        NULL,
    last_login_ip       VARCHAR(45)     NULL        COMMENT 'IPv4 or IPv6',
    password_changed_at DATETIME        NULL,
    deleted_at          DATETIME        NULL        COMMENT 'NULL = not deleted (soft-delete)',
    created_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by          VARCHAR(50)     NULL,
    updated_by          VARCHAR(50)     NULL,

    PRIMARY KEY (id),
    UNIQUE KEY  uk_users_username  (username),
    UNIQUE KEY  uk_users_email     (email),
    KEY         idx_users_role       (role),
    KEY         idx_users_active     (active),
    KEY         idx_users_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Platform users — RBAC roles enforced at gateway and service level';

-- =============================================================
-- 2. user_sessions
-- =============================================================
CREATE TABLE user_sessions (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    user_id         BIGINT          NOT NULL,
    jti             VARCHAR(36)     NOT NULL    COMMENT 'JWT ID claim (UUID) — revocation lookup key',
    jwt_token_hash  VARCHAR(64)     NULL        COMMENT 'SHA-256(token) — optional direct revocation check',
    ip_address      VARCHAR(45)     NULL,
    user_agent      TEXT            NULL,
    issued_at       DATETIME        NOT NULL,
    expires_at      DATETIME        NOT NULL,
    revoked_at      DATETIME        NULL        COMMENT 'NULL = still valid',
    revoke_reason   VARCHAR(50)     NULL        COMMENT 'LOGOUT | PASSWORD_CHANGED | ADMIN_REVOKE | ROLE_CHANGE',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    UNIQUE KEY  uk_sessions_jti        (jti),
    KEY         idx_sessions_user_id   (user_id),
    KEY         idx_sessions_expires   (expires_at),
    KEY         idx_sessions_revoked   (revoked_at),
    CONSTRAINT  fk_sessions_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='JWT session tracking — supports per-jti revocation. Queried by jti on every request.';

-- =============================================================
-- 3. system_config
-- =============================================================
CREATE TABLE system_config (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    config_key      VARCHAR(100)    NOT NULL,
    config_value    TEXT            NOT NULL,
    description     VARCHAR(255)    NULL,
    updated_by      VARCHAR(50)     NULL,
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    UNIQUE KEY  uk_system_config_key (config_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Auth-domain runtime config (JWT expiry, lockout policy). Not mixed with AI or Ollama config.';

-- =============================================================
-- 4. switch_profiles
-- =============================================================
CREATE TABLE switch_profiles (
    id                    BIGINT          NOT NULL AUTO_INCREMENT,
    profile_name          VARCHAR(100)    NOT NULL,
    description           TEXT            NULL,
    environment           ENUM('PROD','UAT','DEV') NOT NULL DEFAULT 'DEV',
    host                  VARCHAR(255)    NOT NULL,
    port                  INT             NOT NULL    COMMENT 'TCP port 1–65535',
    timezone              VARCHAR(100)    NOT NULL DEFAULT 'UTC'
                                                      COMMENT 'IANA tz — for DE7/DE12/DE13 parsing',
    connection_timeout_ms INT             NOT NULL DEFAULT 30000,
    tpdu_enabled          BOOLEAN         NOT NULL DEFAULT FALSE,
    tpdu_value            VARCHAR(20)     NULL        COMMENT '10-digit TPDU header — required when tpdu_enabled = TRUE',
    active                BOOLEAN         NOT NULL DEFAULT FALSE,
    is_default            BOOLEAN         NOT NULL DEFAULT FALSE
                                                      COMMENT 'Only one row may be TRUE — enforced at service level',
    last_tested_at        DATETIME        NULL,
    last_test_result      ENUM('OK','FAILED','UNTESTED') NOT NULL DEFAULT 'UNTESTED',
    last_test_latency_ms  INT             NULL,
    last_test_message     VARCHAR(500)    NULL,
    deleted_at            DATETIME        NULL,
    created_at            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by            VARCHAR(50)     NULL,
    updated_by            VARCHAR(50)     NULL,

    PRIMARY KEY (id),
    UNIQUE KEY  uk_profiles_name         (profile_name),
    KEY         idx_profiles_environment (environment),
    KEY         idx_profiles_active      (active),
    KEY         idx_profiles_is_default  (is_default),
    KEY         idx_profiles_deleted_at  (deleted_at),
    CONSTRAINT  chk_profiles_port  CHECK (port BETWEEN 1 AND 65535),
    CONSTRAINT  chk_profiles_tpdu  CHECK (tpdu_enabled = FALSE OR tpdu_value IS NOT NULL)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Switch connection profiles. Owns host/port/timezone/format. Rules live in validation_rules.';

-- =============================================================
-- 5. message_formats
-- =============================================================
CREATE TABLE message_formats (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    profile_id      BIGINT          NOT NULL,
    format_name     VARCHAR(100)    NOT NULL,
    iso_version     VARCHAR(60)     NULL        COMMENT 'e.g. ISO 8583-1:1987',
    encoding        ENUM('ASCII','EBCDIC','Binary') NOT NULL DEFAULT 'ASCII',
    mti             VARCHAR(4)      NULL        COMMENT 'Primary MTI this format targets (informational)',
    xml_content     LONGTEXT        NOT NULL    COMMENT 'Current jPOS GenericPackager XML',
    checksum        VARCHAR(64)     NULL        COMMENT 'SHA-256 of xml_content — change detection',
    current_version INT             NOT NULL DEFAULT 1,
    deleted_at      DATETIME        NULL,
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by      VARCHAR(50)     NULL,
    updated_by      VARCHAR(50)     NULL,

    PRIMARY KEY (id),
    UNIQUE KEY  uk_formats_profile_name  (profile_id, format_name),
    KEY         idx_formats_profile_id   (profile_id),
    KEY         idx_formats_mti          (mti),
    KEY         idx_formats_deleted_at   (deleted_at),
    CONSTRAINT  fk_formats_profile
        FOREIGN KEY (profile_id) REFERENCES switch_profiles (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='ISO 8583 format definitions. XML drives jPOS GenericPackager in the validation engine.';

-- =============================================================
-- 6. message_format_versions
-- =============================================================
CREATE TABLE message_format_versions (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    format_id       BIGINT          NOT NULL,
    version_number  INT             NOT NULL,
    xml_content     LONGTEXT        NOT NULL,
    checksum        VARCHAR(64)     NOT NULL    COMMENT 'SHA-256 of xml_content — tamper detection',
    change_note     VARCHAR(500)    NULL,
    is_current      BOOLEAN         NOT NULL DEFAULT FALSE,
    validated_ok    BOOLEAN         NOT NULL DEFAULT FALSE
                                                COMMENT 'TRUE = jPOS successfully loaded this XML',
    validated_at    DATETIME        NULL,
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      VARCHAR(50)     NULL,

    PRIMARY KEY (id),
    UNIQUE KEY  uk_format_versions    (format_id, version_number),
    KEY         idx_fmv_format_id     (format_id),
    KEY         idx_fmv_is_current    (is_current),
    CONSTRAINT  fk_fmv_format
        FOREIGN KEY (format_id) REFERENCES message_formats (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Version history of format XML — enables rollback without data loss.';

-- =============================================================
-- 7. validation_rules
-- =============================================================
CREATE TABLE validation_rules (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    profile_id      BIGINT          NOT NULL    COMMENT 'Ref to switch_profiles.id — no FK (same DB, cross-domain boundary)',
    profile_name    VARCHAR(100)    NOT NULL    COMMENT 'Snapshot — avoids join on display',
    mti             VARCHAR(4)      NOT NULL    COMMENT 'e.g. 0200, 0210, 0420, 0800',
    de_number       VARCHAR(10)     NOT NULL    COMMENT 'e.g. DE2, DE3, MTI',
    field_name      VARCHAR(150)    NOT NULL,
    is_mandatory    BOOLEAN         NOT NULL DEFAULT FALSE,
    min_length      INT             NULL,
    max_length      INT             NULL,
    exact_length    INT             NULL        COMMENT 'Shorthand when min = max',
    data_type       ENUM('numeric','alpha','alphanumeric','binary','special') NOT NULL,
    pattern_regex   VARCHAR(500)    NULL        COMMENT 'Java regex — applied after type check. NULL = skip.',
    severity        ENUM('CRITICAL','WARNING','INFO') NOT NULL DEFAULT 'CRITICAL',
    priority        INT             NOT NULL DEFAULT 1   COMMENT 'Lower number = evaluated first',
    active          BOOLEAN         NOT NULL DEFAULT TRUE,
    effective_from  DATE            NULL        COMMENT 'NULL = effective immediately',
    effective_to    DATE            NULL        COMMENT 'NULL = no expiry',
    description     TEXT            NULL,
    deleted_at      DATETIME        NULL,
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by      VARCHAR(50)     NULL,
    updated_by      VARCHAR(50)     NULL,

    PRIMARY KEY (id),
    UNIQUE KEY  uk_rules_profile_mti_de     (profile_id, mti, de_number)
                                             COMMENT 'One rule per DE per MTI per profile',
    KEY         idx_rules_profile_mti       (profile_id, mti),
    KEY         idx_rules_active            (active),
    KEY         idx_rules_deleted_at        (deleted_at),
    KEY         idx_rules_priority          (priority),
    KEY         idx_rules_effective         (effective_from, effective_to),
    CONSTRAINT  chk_rules_length  CHECK (min_length IS NULL OR max_length IS NULL OR min_length <= max_length)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Per-profile per-MTI per-DE validation rules. Editable at runtime — no redeploy needed.';

-- =============================================================
-- 8. rule_allowed_values
-- =============================================================
CREATE TABLE rule_allowed_values (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    rule_id         BIGINT          NOT NULL,
    allowed_value   VARCHAR(255)    NOT NULL,
    value_label     VARCHAR(255)    NULL        COMMENT 'Human-readable e.g. Purchase, Cash Advance',
    sort_order      INT             NOT NULL DEFAULT 0,
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      VARCHAR(50)     NULL,

    PRIMARY KEY (id),
    UNIQUE KEY  uk_rule_allowed_value        (rule_id, allowed_value),
    KEY         idx_allowed_values_rule_id   (rule_id),
    CONSTRAINT  fk_allowed_values_rule
        FOREIGN KEY (rule_id) REFERENCES validation_rules (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Enum-style allowed values per validation rule. Cascade-deletes with rule.';

-- =============================================================
-- 9. field_definitions
-- =============================================================
CREATE TABLE field_definitions (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,
    profile_id          BIGINT          NOT NULL    COMMENT 'Ref to switch_profiles.id — no FK',
    mti                 VARCHAR(4)      NOT NULL,
    de_number           VARCHAR(10)     NOT NULL    COMMENT 'e.g. DE2, DE3, MTI',
    field_name          VARCHAR(100)    NOT NULL,
    data_type           ENUM('numeric','alpha','alphanumeric','binary','special') NOT NULL,
    max_length          INT             NULL,
    is_llvar            BOOLEAN         NOT NULL DEFAULT FALSE  COMMENT 'Length-prefixed 2-digit',
    is_lllvar           BOOLEAN         NOT NULL DEFAULT FALSE  COMMENT 'Length-prefixed 3-digit',
    is_mandatory        BOOLEAN         NOT NULL DEFAULT FALSE  COMMENT 'Pre-selects field in builder UI',
    placeholder_value   VARCHAR(500)    NULL        COMMENT 'Example value shown in builder form',
    display_order       INT             NOT NULL DEFAULT 0      COMMENT 'Mandatory fields shown first',
    is_builder_visible  BOOLEAN         NOT NULL DEFAULT TRUE   COMMENT 'FALSE hides bitmap, DE1 etc.',
    description         VARCHAR(255)    NULL,
    deleted_at          DATETIME        NULL,
    created_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by          VARCHAR(50)     NULL,
    updated_by          VARCHAR(50)     NULL,

    PRIMARY KEY (id),
    UNIQUE KEY  uk_fielddef_profile_mti_de  (profile_id, mti, de_number),
    KEY         idx_fielddef_profile_mti    (profile_id, mti),
    KEY         idx_fielddef_deleted_at     (deleted_at),
    KEY         idx_fielddef_display_order  (display_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='DE field catalog per profile per MTI. Drives message builder UI. Replaces hardcoded PROFILE_DE_CATALOG in frontend.';

-- =============================================================
-- 10. ai_prompt_templates
-- =============================================================
CREATE TABLE ai_prompt_templates (
    id               BIGINT          NOT NULL AUTO_INCREMENT,
    template_name    VARCHAR(100)    NOT NULL,
    scope            ENUM('GLOBAL','PROFILE') NOT NULL DEFAULT 'GLOBAL',
    profile_id       BIGINT          NULL        COMMENT 'NULL for GLOBAL. Ref to switch_profiles.id — no FK.',
    profile_name     VARCHAR(100)    NULL        COMMENT 'Snapshot for PROFILE-scope templates',
    prompt_template  LONGTEXT        NOT NULL    COMMENT 'Variables: {mti} {profile} {errors} {fields}',
    variables_used   VARCHAR(500)    NULL        COMMENT 'JSON array of variable names used in template',
    current_version  INT             NOT NULL DEFAULT 1,
    active           BOOLEAN         NOT NULL DEFAULT TRUE,
    deleted_at       DATETIME        NULL,
    created_at       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by       VARCHAR(50)     NULL,
    updated_by       VARCHAR(50)     NULL,

    PRIMARY KEY (id),
    UNIQUE KEY  uk_apt_scope_profile    (scope, profile_id)
                                         COMMENT 'One template per scope+profile combo',
    KEY         idx_apt_scope           (scope),
    KEY         idx_apt_profile_id      (profile_id),
    KEY         idx_apt_active          (active),
    KEY         idx_apt_deleted_at      (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='AI prompt templates. GLOBAL is fallback; PROFILE-scope overrides win.';

-- =============================================================
-- 11. ai_prompt_template_versions
-- =============================================================
CREATE TABLE ai_prompt_template_versions (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    template_id     BIGINT          NOT NULL,
    version_number  INT             NOT NULL,
    prompt_content  LONGTEXT        NOT NULL,
    change_note     VARCHAR(500)    NULL,
    is_current      BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      VARCHAR(50)     NULL,

    PRIMARY KEY (id),
    UNIQUE KEY  uk_aptv_template_version    (template_id, version_number),
    KEY         idx_aptv_template_id        (template_id),
    KEY         idx_aptv_is_current         (is_current),
    CONSTRAINT  fk_aptv_template
        FOREIGN KEY (template_id) REFERENCES ai_prompt_templates (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Version history of AI prompts — enables rollback without data loss.';

-- =============================================================
-- 12. ai_run_logs
-- =============================================================
CREATE TABLE ai_run_logs (
    id                   BIGINT          NOT NULL AUTO_INCREMENT,
    run_reference        VARCHAR(30)     NOT NULL    COMMENT 'e.g. VLD-20250514-00041 — cross-domain ref, no FK',
    template_id          BIGINT          NULL,
    template_scope_used  ENUM('GLOBAL','PROFILE') NULL,
    profile_id           BIGINT          NULL,
    ollama_endpoint      VARCHAR(500)    NULL,
    model_name           VARCHAR(100)    NULL,
    prompt_sent          LONGTEXT        NULL,
    response_received    LONGTEXT        NULL,
    http_status_code     INT             NULL,
    status               ENUM('SUCCESS','FAILED','SKIPPED','CB_OPEN','TIMEOUT') NOT NULL,
    duration_ms          BIGINT          NULL,
    retry_count          INT             NOT NULL DEFAULT 0,
    error_message        TEXT            NULL,
    correlation_id       VARCHAR(36)     NULL,
    created_at           DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    KEY         idx_ai_logs_run_reference   (run_reference),
    KEY         idx_ai_logs_status          (status),
    KEY         idx_ai_logs_model_name      (model_name),
    KEY         idx_ai_logs_created_at      (created_at),
    CONSTRAINT  fk_ai_logs_template
        FOREIGN KEY (template_id) REFERENCES ai_prompt_templates (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Every Ollama API call logged — for debugging, latency analysis, and retry tracking.';

-- =============================================================
-- 13. ollama_config
-- =============================================================
CREATE TABLE ollama_config (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    config_key      VARCHAR(100)    NOT NULL,
    config_value    VARCHAR(500)    NOT NULL,
    description     VARCHAR(255)    NULL,
    is_sensitive    BOOLEAN         NOT NULL DEFAULT FALSE  COMMENT 'TRUE = mask value in API responses',
    updated_by      VARCHAR(50)     NULL,
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    UNIQUE KEY  uk_ollama_config_key (config_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='AI service config (endpoint, model, timeouts). Isolated from auth system_config.';

-- =============================================================
-- 14. validation_runs
-- =============================================================
CREATE TABLE validation_runs (
    id                      BIGINT          NOT NULL AUTO_INCREMENT,
    run_reference           VARCHAR(30)     NOT NULL    COMMENT 'e.g. VLD-20250514-00041 — human-readable unique key',
    profile_id              BIGINT          NULL        COMMENT 'Ref to switch_profiles.id — NULL if profile deleted',
    profile_name_snapshot   VARCHAR(100)    NULL        COMMENT 'Profile name captured at time of run',
    format_id               BIGINT          NULL,
    format_name_snapshot    VARCHAR(100)    NULL,
    user_id                 BIGINT          NULL        COMMENT 'Ref to users.id — NULL if user deleted',
    username_snapshot       VARCHAR(50)     NULL        COMMENT 'Username captured at time of run',
    user_role_snapshot      VARCHAR(20)     NULL,
    mti                     VARCHAR(4)      NULL,
    mti_description         VARCHAR(100)    NULL        COMMENT 'e.g. Authorization Request',
    bitmap_primary          VARCHAR(16)     NULL        COMMENT '8-byte primary bitmap hex',
    bitmap_extended         VARCHAR(16)     NULL        COMMENT '8-byte secondary bitmap hex — NULL if not extended',
    status                  ENUM('VALID','INVALID','WARNED','ERROR','PARSE_ERROR') NOT NULL,
    total_fields_present    INT             NOT NULL DEFAULT 0,
    total_errors            INT             NOT NULL DEFAULT 0,
    critical_count          INT             NOT NULL DEFAULT 0,
    warning_count           INT             NOT NULL DEFAULT 0,
    info_count              INT             NOT NULL DEFAULT 0,
    response_code           VARCHAR(2)      NULL        COMMENT 'DE39 if present',
    response_label          VARCHAR(100)    NULL        COMMENT 'e.g. Approved, Do Not Honor',
    transaction_amount      BIGINT          NULL        COMMENT 'Minor units — 10000 = ₹100.00',
    currency_code           VARCHAR(3)      NULL        COMMENT 'ISO 4217 e.g. 356 = INR',
    merchant_name           VARCHAR(100)    NULL,
    terminal_id             VARCHAR(20)     NULL,
    pan_masked              VARCHAR(25)     NULL        COMMENT 'e.g. 4111 •••• •••• 1111 — NEVER store raw PAN',
    hex_message_hash        VARCHAR(64)     NULL        COMMENT 'SHA-256 of raw message — rerun dedup',
    parse_duration_ms       INT             NULL,
    validation_duration_ms  INT             NULL,
    ai_duration_ms          INT             NULL,
    total_duration_ms       INT             NULL,
    ai_enabled              BOOLEAN         NOT NULL DEFAULT FALSE,
    ai_explanation          LONGTEXT        NULL,
    ai_model_used           VARCHAR(100)    NULL,
    is_rerun                BOOLEAN         NOT NULL DEFAULT FALSE,
    original_run_reference  VARCHAR(30)     NULL,
    client_ip               VARCHAR(45)     NULL,
    correlation_id          VARCHAR(36)     NULL,
    deleted_at              DATETIME        NULL        COMMENT 'Soft-delete — ADMIN only',
    created_at              DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    UNIQUE KEY  uk_run_reference            (run_reference),
    KEY         idx_runs_profile_id         (profile_id),
    KEY         idx_runs_user_id            (user_id),
    KEY         idx_runs_mti                (mti),
    KEY         idx_runs_status             (status),
    KEY         idx_runs_response_code      (response_code),
    KEY         idx_runs_created_at         (created_at),
    KEY         idx_runs_deleted_at         (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Full audit of every validation request. Written async via RabbitMQ by history service.';

-- =============================================================
-- 15. validation_run_fields
-- =============================================================
CREATE TABLE validation_run_fields (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    run_id          BIGINT          NOT NULL,
    de_number       VARCHAR(10)     NOT NULL    COMMENT 'e.g. MTI, DE2, DE3',
    field_name      VARCHAR(150)    NULL,
    raw_value       LONGTEXT        NULL        COMMENT 'Parsed value — PAN stored masked here',
    display_value   VARCHAR(500)    NULL        COMMENT 'Formatted: ₹100.00, 4111 •••• •••• 1111, etc.',
    is_present      BOOLEAN         NOT NULL DEFAULT FALSE,
    field_length    INT             NULL,
    de_position     INT             NULL        COMMENT 'DE bit number 1–128',
    encoding_type   ENUM('FIXED','LLVAR','LLLVAR','MTI','BITMAP') NULL,
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    KEY         idx_vrf_run_id      (run_id),
    KEY         idx_vrf_de_number   (de_number),
    KEY         idx_vrf_de_position (de_position),
    CONSTRAINT  fk_vrf_run
        FOREIGN KEY (run_id) REFERENCES validation_runs (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='All DE fields parsed from a validation run. Cascade-deletes with run.';

-- =============================================================
-- 16. validation_run_errors
-- =============================================================
CREATE TABLE validation_run_errors (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,
    run_id              BIGINT          NOT NULL,
    rule_id             BIGINT          NULL        COMMENT 'Ref to validation_rules.id — NULL if rule deleted after run',
    de_number           VARCHAR(10)     NOT NULL,
    field_name          VARCHAR(150)    NULL,
    severity            ENUM('CRITICAL','WARNING','INFO') NOT NULL,
    error_code          VARCHAR(50)     NOT NULL
                                         COMMENT 'MANDATORY_ABSENT | LENGTH_TOO_SHORT | LENGTH_TOO_LONG | TYPE_MISMATCH | PATTERN_MISMATCH | VALUE_NOT_ALLOWED',
    error_message       VARCHAR(500)    NOT NULL,
    rule_snapshot       VARCHAR(500)    NULL        COMMENT 'Rule definition at time of run e.g. mandatory=true, maxLen=10',
    expected_value      VARCHAR(500)    NULL,
    actual_value        VARCHAR(500)    NULL,
    ai_explanation      TEXT            NULL,
    ai_fix_suggestion   TEXT            NULL,
    created_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    KEY         idx_vre_run_id      (run_id),
    KEY         idx_vre_severity    (severity),
    KEY         idx_vre_error_code  (error_code),
    CONSTRAINT  fk_vre_run
        FOREIGN KEY (run_id) REFERENCES validation_runs (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Individual validation errors per run. Includes AI fix suggestions when AI is enabled.';

-- =============================================================
-- 17. audit_logs    ← FIX: PRIMARY KEY was missing before
-- =============================================================
CREATE TABLE audit_logs (
    id                BIGINT          NOT NULL AUTO_INCREMENT,
    action            VARCHAR(100)    NOT NULL    COMMENT 'e.g. auth.login, profile.create, rule.update',
    entity_type       VARCHAR(50)     NULL        COMMENT 'RULE | FORMAT | PROFILE | USER | AI_CONFIG | AI_PROMPT | FIELD_DEFINITION',
    entity_id         VARCHAR(50)     NULL,
    entity_name       VARCHAR(200)    NULL,
    user_id           BIGINT          NULL        COMMENT 'Ref to users.id — NULL if user deleted',
    username_snapshot VARCHAR(50)     NULL,
    user_role         VARCHAR(20)     NULL,
    source_service    VARCHAR(50)     NULL        COMMENT 'auth | rules | profile | ai | history',
    correlation_id    VARCHAR(36)     NULL,
    ip_address        VARCHAR(45)     NULL,
    old_value         JSON            NULL        COMMENT 'State before change (JSON snapshot)',
    new_value         JSON            NULL        COMMENT 'State after change (JSON snapshot)',
    created_at        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- NO updated_at  — audit rows are IMMUTABLE
    -- NO deleted_at  — audit rows are IMMUTABLE, never soft-deleted

    PRIMARY KEY (id),
    KEY         idx_audit_action          (action),
    KEY         idx_audit_entity_type     (entity_type),
    KEY         idx_audit_entity_id       (entity_id),
    KEY         idx_audit_user_id         (user_id),
    KEY         idx_audit_source_service  (source_service),
    KEY         idx_audit_correlation_id  (correlation_id),
    KEY         idx_audit_created_at      (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Immutable audit trail from all services via RabbitMQ. NEVER UPDATE or DELETE any row.';

-- =============================================================
-- RE-ENABLE FK CHECKS
-- =============================================================
SET foreign_key_checks = 1;

-- =============================================================
-- VERIFY
-- =============================================================
SELECT
    table_name                          AS `table`,
    table_rows                          AS `rows (est)`,
    ROUND(data_length / 1024, 1)        AS `data_kb`,
    table_comment                       AS `comment`
FROM   information_schema.tables
WHERE  table_schema = 'iso_validator_db'
ORDER  BY table_name;