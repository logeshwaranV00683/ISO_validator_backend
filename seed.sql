-- ================================================================
-- ISO Validator — Seed Data
-- Database: ISO_validator_db
-- Run after schema.sql
--
-- Sections:
--   1. ollama_config       (9 rows)
--   2. system_config       (4 rows)
--   3. users               (3 rows: admin, analyst, viewer)
--   4. switch_profiles     (2 rows: UAT + DEV demo profiles)
--   5. message_formats     (1 row: basic 0200 format)
--   6. validation_rules    (10 rows: common DE rules for 0200)
--   7. rule_allowed_values (5 rows: DE3 processing codes)
--   8. field_definitions   (15 rows: DE catalog for 0200 builder)
--   9. ai_prompt_templates (1 row: global template)
-- ================================================================

USE iso_validator_db;

-- ================================================================
-- 1. OLLAMA CONFIG (9 rows)
--    Owned by: ai-service
--    Keys read at startup — change via AI config API at runtime
-- ================================================================

INSERT INTO ollama_config (config_key, config_value, description, is_sensitive) VALUES
('ollama.enabled',          'true',                    'Enable/disable Ollama AI integration globally',    FALSE),
('ollama.endpoint',         'http://localhost:11434',  'Ollama API base URL',                              FALSE),
('ollama.model',            'mistral:7b',              'Default model for explanations',                   FALSE),
('ollama.timeout.ms',       '20000',                   'HTTP request timeout in milliseconds',             FALSE),
('ollama.max.tokens',       '500',                     'Max tokens in Ollama response',                    FALSE),
('ollama.temperature',      '0.3',                     'Model temperature (0.0 = deterministic)',          FALSE),
('ollama.retry.count',      '2',                       'Number of retries on connection failure',          FALSE),
('ollama.prompt.language',  'en',                      'Response language (en / ta / hi)',                 FALSE),
('ollama.log.prompts',      'false',                   'Log full prompts to ai_run_logs (disable in prod)',FALSE);


-- ================================================================
-- 2. SYSTEM CONFIG (4 rows)
--    Owned by: auth-service
--    Drives JWT expiry, lockout policy, pagination defaults
-- ================================================================

INSERT INTO system_config (config_key, config_value, description, updated_by) VALUES
('jwt.expiry.minutes',      '60',  'JWT access token expiry in minutes',           'SYSTEM'),
('login.max.failures',      '5',   'Max consecutive failed logins before lockout',  'SYSTEM'),
('account.lock.minutes',    '15',  'Account lock duration in minutes',              'SYSTEM'),
('pagination.default.size', '20',  'Default page size for all paginated list APIs', 'SYSTEM');


-- ================================================================
-- 3. USERS (3 rows)
--    One per role: ADMIN, ANALYST, VIEWER
--
--    Passwords (BCrypt $2a$10$ rounds):
--      admin   → Admin@123
--      analyst → Analyst@123
--      viewer  → Viewer@123
--
--    ⚠ These are DEV-only seed passwords.
--      Force password change on first login in UAT/PROD.
-- ================================================================

INSERT INTO users
    (username, password_hash, full_name, email, avatar_initials, role, active, created_by)
VALUES
(
    'admin',
    '$2a$12$6677N8WveOcQZS2IWDF9LOupWTvqunLpBXQQBtn7DMMQbHoNrIXSi',
    -- password: Admin@123
    'System Admin',
    'admin@iso8583.local',
    'SA',
    'ADMIN',
    TRUE,
    'SYSTEM'
),
(
    'analyst',
    '$2a$12$GW7ERcAtoYGy8MyAqRICkOptsIT723AA5.HJ2OCuCvfTFUNmCMGVi',
    -- password: Analyst@123
    'Test Analyst',
    'analyst@iso8583.local',
    'AL',
    'ANALYST',
    TRUE,
    'SYSTEM'
),
(
    'viewer',
    '$2a$12$LWIYgpidES4N7pIYfof/UugoAeeU4LnNTHfjgR0/v2fAAfl4TGMgC',
    -- password: Viewer@123
    'Test Viewer',
    'viewer@iso8583.local',
    'TV',
    'VIEWER',
    TRUE,
    'SYSTEM'
);


-- ================================================================
-- 4. SWITCH PROFILES (2 rows)
--    UAT profile  — active=TRUE, is_default=TRUE
--    DEV profile  — active=TRUE, is_default=FALSE
--    host/port are placeholder values — update before TCP test
-- ================================================================

INSERT INTO switch_profiles
    (profile_name, description, environment, host, port, timezone,
     connection_timeout_ms, tpdu_enabled, active, is_default,
     last_test_result, created_by)
VALUES
(
    'UAT Switch',
    'UAT payment switch — used for sprint testing and integration validation.',
    'UAT',
    '192.168.1.100',
    8583,
    'Asia/Kolkata',
    30000,
    FALSE,
    TRUE,
    TRUE,
    'UNTESTED',
    'admin'
),
(
    'DEV Switch',
    'Local development switch simulator. Replace host/port with your simulator.',
    'DEV',
    '127.0.0.1',
    9999,
    'Asia/Kolkata',
    10000,
    FALSE,
    TRUE,
    FALSE,
    'UNTESTED',
    'admin'
);


-- ================================================================
-- 5. MESSAGE FORMATS (1 row)
--    Basic ISO 8583-1:1987 GenericPackager XML for MTI 0200
--    profile_id=1 → UAT Switch
--    This is a minimal working packager — expand DEs as needed
-- ================================================================

INSERT INTO message_formats
    (profile_id, format_name, iso_version, encoding, mti, xml_content,
     checksum, current_version, created_by)
VALUES
(
    1,
    'ISO8583-1987 Base',
    'ISO 8583-1:1987',
    'ASCII',
    '0200',
    '<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE isopackager PUBLIC
  "-//jPOS//DTD ISOPackager DTD//EN"
  "http://jpos.org/dtd/genericpackager.dtd">
<isopackager>
  <isofield id="0"  length="4"   name="MESSAGE TYPE INDICATOR"          class="org.jpos.iso.IFA_NUMERIC"/>
  <isofield id="1"  length="8"   name="BIT MAP"                         class="org.jpos.iso.IFA_BITMAP"/>
  <isofield id="2"  length="19"  name="PRIMARY ACCOUNT NUMBER"          class="org.jpos.iso.IFA_LLNUM"/>
  <isofield id="3"  length="6"   name="PROCESSING CODE"                 class="org.jpos.iso.IFA_NUMERIC"/>
  <isofield id="4"  length="12"  name="AMOUNT TRANSACTION"              class="org.jpos.iso.IFA_NUMERIC"/>
  <isofield id="7"  length="10"  name="DATE AND TIME TRANSMISSION"      class="org.jpos.iso.IFA_NUMERIC"/>
  <isofield id="11" length="6"   name="SYSTEM TRACE AUDIT NUMBER"       class="org.jpos.iso.IFA_NUMERIC"/>
  <isofield id="12" length="6"   name="LOCAL TRANSACTION TIME"          class="org.jpos.iso.IFA_NUMERIC"/>
  <isofield id="13" length="4"   name="LOCAL TRANSACTION DATE"          class="org.jpos.iso.IFA_NUMERIC"/>
  <isofield id="14" length="4"   name="EXPIRATION DATE"                 class="org.jpos.iso.IFA_NUMERIC"/>
  <isofield id="22" length="3"   name="POS ENTRY MODE"                  class="org.jpos.iso.IFA_NUMERIC"/>
  <isofield id="35" length="37"  name="TRACK 2 DATA"                    class="org.jpos.iso.IFA_LLCHAR"/>
  <isofield id="37" length="12"  name="RETRIEVAL REFERENCE NUMBER"      class="org.jpos.iso.IFA_CHAR"/>
  <isofield id="41" length="8"   name="CARD ACCEPTOR TERMINAL ID"       class="org.jpos.iso.IFA_CHAR"/>
  <isofield id="42" length="15"  name="CARD ACCEPTOR ID CODE"           class="org.jpos.iso.IFA_CHAR"/>
  <isofield id="43" length="40"  name="CARD ACCEPTOR NAME/LOCATION"     class="org.jpos.iso.IFA_CHAR"/>
  <isofield id="49" length="3"   name="CURRENCY CODE TRANSACTION"       class="org.jpos.iso.IFA_NUMERIC"/>
  <isofield id="52" length="8"   name="PIN DATA"                        class="org.jpos.iso.IFA_BINARY"/>
  <isofield id="55" length="255" name="ICC DATA"                        class="org.jpos.iso.IFA_LLLCHAR"/>
  <isofield id="60" length="999" name="PRIVATE RESERVED 1"              class="org.jpos.iso.IFA_LLLCHAR"/>
  <isofield id="62" length="999" name="PRIVATE RESERVED 3"              class="org.jpos.iso.IFA_LLLCHAR"/>
</isopackager>',
    SHA2('minimal-packager-v1', 256),
    1,
    'admin'
);


-- ================================================================
-- 6. VALIDATION RULES (10 rows)
--    All for profile_id=1, MTI=0200
--    Covers the most common DE checks for an authorization request
-- ================================================================

INSERT INTO validation_rules
    (profile_id, profile_name, mti, de_number, field_name,
     is_mandatory, min_length, max_length, data_type,
     pattern_regex, severity, priority, active, created_by)
VALUES
-- DE2 — PAN: mandatory, 13–19 digits, Luhn not checked here (done in code)
(1, 'UAT Switch', '0200', 'DE2',  'Primary Account Number',
 TRUE,  13, 19, 'numeric',      '^[0-9]{13,19}$',            'CRITICAL', 1, TRUE, 'admin'),

-- DE3 — Processing Code: mandatory, exactly 6 digits
(1, 'UAT Switch', '0200', 'DE3',  'Processing Code',
 TRUE,  6,  6,  'numeric',      '^[0-9]{6}$',                'CRITICAL', 2, TRUE, 'admin'),

-- DE4 — Amount: mandatory, exactly 12 digits
(1, 'UAT Switch', '0200', 'DE4',  'Amount Transaction',
 TRUE,  12, 12, 'numeric',      '^[0-9]{12}$',               'CRITICAL', 3, TRUE, 'admin'),

-- DE7 — Transmission date/time: mandatory, exactly 10 digits (MMDDhhmmss)
(1, 'UAT Switch', '0200', 'DE7',  'Transmission Date and Time',
 TRUE,  10, 10, 'numeric',      '^(0[1-9]|1[0-2])(0[1-9]|[12][0-9]|3[01])[0-2][0-9][0-5][0-9][0-5][0-9]$',
 'CRITICAL', 4, TRUE, 'admin'),

-- DE11 — STAN: mandatory, exactly 6 digits
(1, 'UAT Switch', '0200', 'DE11', 'System Trace Audit Number',
 TRUE,  6,  6,  'numeric',      '^[0-9]{6}$',                'CRITICAL', 5, TRUE, 'admin'),

-- DE14 — Expiry date: optional, exactly 4 digits (YYMM)
(1, 'UAT Switch', '0200', 'DE14', 'Expiration Date',
 FALSE, 4,  4,  'numeric',      '^[0-9]{2}(0[1-9]|1[0-2])$', 'WARNING', 6, TRUE, 'admin'),

-- DE22 — POS Entry Mode: mandatory, 3 digits
(1, 'UAT Switch', '0200', 'DE22', 'POS Entry Mode',
 TRUE,  3,  3,  'numeric',      '^[0-9]{3}$',                'CRITICAL', 7, TRUE, 'admin'),

-- DE37 — RRN: mandatory, exactly 12 alphanumeric
(1, 'UAT Switch', '0200', 'DE37', 'Retrieval Reference Number',
 TRUE,  12, 12, 'alphanumeric', '^[A-Za-z0-9]{12}$',         'CRITICAL', 8, TRUE, 'admin'),

-- DE41 — Terminal ID: mandatory, 8 chars
(1, 'UAT Switch', '0200', 'DE41', 'Card Acceptor Terminal ID',
 TRUE,  8,  8,  'alphanumeric', '^[A-Za-z0-9 ]{8}$',         'CRITICAL', 9, TRUE, 'admin'),

-- DE49 — Currency Code: mandatory, exactly 3 digits (ISO 4217)
(1, 'UAT Switch', '0200', 'DE49', 'Currency Code Transaction',
 TRUE,  3,  3,  'numeric',      '^[0-9]{3}$',                'CRITICAL', 10, TRUE, 'admin');


-- ================================================================
-- 7. RULE ALLOWED VALUES (5 rows)
--    DE3 Processing Code — first 2 digits (transaction type)
--    Common values: 00=Purchase, 01=Withdrawal, 20=Refund, 28=Balance Inquiry, 31=Balance Inquiry
-- ================================================================

-- Get the DE3 rule id
SET @de3_rule_id = (
    SELECT id FROM validation_rules
    WHERE profile_id = 1 AND mti = '0200' AND de_number = 'DE3'
    LIMIT 1
);

INSERT INTO rule_allowed_values (rule_id, allowed_value, value_label, sort_order, created_by) VALUES
(@de3_rule_id, '000000', 'Purchase',              1, 'admin'),
(@de3_rule_id, '010000', 'Cash Withdrawal',        2, 'admin'),
(@de3_rule_id, '200000', 'Refund / Return',         3, 'admin'),
(@de3_rule_id, '280000', 'Balance Inquiry',         4, 'admin'),
(@de3_rule_id, '310000', 'Balance Inquiry (ATM)',   5, 'admin');


-- ================================================================
-- 8. FIELD DEFINITIONS (15 rows)
--    For profile_id=1, MTI=0200
--    Drives the message builder UI — what fields are shown,
--    in what order, with what placeholder
-- ================================================================

INSERT INTO field_definitions
    (profile_id, mti, de_number, field_name, data_type, max_length,
     is_llvar, is_lllvar, is_mandatory, placeholder_value,
     display_order, is_builder_visible, description, created_by)
VALUES
-- MTI always present — hidden from builder (handled by format selection)
(1, '0200', 'MTI',  'Message Type Indicator',        'numeric',      4,   FALSE, FALSE, TRUE,  '0200',                       0,  FALSE, 'ISO 8583 Message Type Indicator',                     'admin'),
-- DE1 bitmap — hidden from builder
(1, '0200', 'DE1',  'Primary Bitmap',                'binary',       8,   FALSE, FALSE, TRUE,  NULL,                         0,  FALSE, 'Auto-generated primary bitmap — not user-editable',   'admin'),
-- Visible mandatory fields
(1, '0200', 'DE2',  'Primary Account Number (PAN)',  'numeric',      19,  TRUE,  FALSE, TRUE,  '4111111111111111',           1,  TRUE,  'Card number — masked in display and storage',          'admin'),
(1, '0200', 'DE3',  'Processing Code',               'numeric',      6,   FALSE, FALSE, TRUE,  '000000',                     2,  TRUE,  'First 2 digits = transaction type e.g. 00=Purchase',  'admin'),
(1, '0200', 'DE4',  'Transaction Amount',            'numeric',      12,  FALSE, FALSE, TRUE,  '000000010000',               3,  TRUE,  'Amount in minor units e.g. 000000010000 = ₹100.00',   'admin'),
(1, '0200', 'DE7',  'Transmission Date & Time',      'numeric',      10,  FALSE, FALSE, TRUE,  '0514143000',                 4,  TRUE,  'MMDDhhmmss format',                                   'admin'),
(1, '0200', 'DE11', 'System Trace Audit Number',     'numeric',      6,   FALSE, FALSE, TRUE,  '000001',                     5,  TRUE,  'Unique per transaction per day',                      'admin'),
(1, '0200', 'DE12', 'Local Transaction Time',        'numeric',      6,   FALSE, FALSE, FALSE, '143000',                     6,  TRUE,  'hhmmss format',                                       'admin'),
(1, '0200', 'DE13', 'Local Transaction Date',        'numeric',      4,   FALSE, FALSE, FALSE, '0514',                       7,  TRUE,  'MMDD format',                                         'admin'),
(1, '0200', 'DE14', 'Expiration Date',               'numeric',      4,   FALSE, FALSE, FALSE, '2612',                       8,  TRUE,  'YYMM format — e.g. 2612 = Dec 2026',                 'admin'),
(1, '0200', 'DE22', 'POS Entry Mode',                'numeric',      3,   FALSE, FALSE, TRUE,  '051',                        9,  TRUE,  '051 = chip+PIN, 071 = contactless',                   'admin'),
(1, '0200', 'DE37', 'Retrieval Reference Number',    'alphanumeric', 12,  FALSE, FALSE, TRUE,  'RRN000000001',               10, TRUE,  '12 alphanumeric — must be unique per transaction',    'admin'),
(1, '0200', 'DE41', 'Terminal ID',                   'alphanumeric', 8,   FALSE, FALSE, TRUE,  'TERM0001',                   11, TRUE,  '8-char terminal identifier',                          'admin'),
(1, '0200', 'DE42', 'Merchant ID',                   'alphanumeric', 15,  FALSE, FALSE, FALSE, 'MERCHANT000001',             12, TRUE,  '15-char merchant identifier',                         'admin'),
(1, '0200', 'DE49', 'Currency Code',                 'numeric',      3,   FALSE, FALSE, TRUE,  '356',                        13, TRUE,  'ISO 4217 — 356 = INR, 840 = USD, 978 = EUR',          'admin');


-- ================================================================
-- 9. AI PROMPT TEMPLATES (1 row — GLOBAL scope)
--    Used by all profiles unless a PROFILE-scope override exists
--    Variables available in the template: {mti} {profile} {errors} {fields}
-- ================================================================

INSERT INTO ai_prompt_templates
    (template_name, scope, profile_id, prompt_template, variables_used,
     current_version, active, created_by)
VALUES
(
    'Global ISO 8583 Validation Explainer',
    'GLOBAL',
    NULL,
    'You are an ISO 8583 payment message expert. A message validation was performed and errors were found.

Message Type: {mti}
Switch Profile: {profile}

Validation Errors:
{errors}

Fields Present in Message:
{fields}

Please do the following:
1. Explain each error in simple, non-technical language that a payment operations analyst would understand.
2. For each error, suggest the exact correction needed.
3. If multiple errors are related (e.g. date/time fields), group them together.
4. Keep the total response under 300 words.
5. Use bullet points for clarity.

Do not include any introductory phrases. Start directly with the error explanations.',
    '["mti","profile","errors","fields"]',
    1,
    TRUE,
    'admin'
);

-- ================================================================
-- Insert corresponding version row for the template
-- ================================================================

SET @template_id = LAST_INSERT_ID();

INSERT INTO ai_prompt_template_versions
    (template_id, version_number, prompt_content, change_note, is_current, created_by)
SELECT
    @template_id,
    1,
    prompt_template,
    'Initial seed version',
    TRUE,
    'admin'
FROM ai_prompt_templates
WHERE id = @template_id;


-- ================================================================
-- ✅ SEED VERIFICATION QUERIES
--    Run these after loading to confirm everything is correct:
--
-- SELECT COUNT(*) FROM ollama_config;        -- expect 9
-- SELECT COUNT(*) FROM system_config;        -- expect 4
-- SELECT COUNT(*) FROM users;                -- expect 3
-- SELECT COUNT(*) FROM switch_profiles;      -- expect 2
-- SELECT COUNT(*) FROM message_formats;      -- expect 1
-- SELECT COUNT(*) FROM validation_rules;     -- expect 10
-- SELECT COUNT(*) FROM rule_allowed_values;  -- expect 5
-- SELECT COUNT(*) FROM field_definitions;    -- expect 15
-- SELECT COUNT(*) FROM ai_prompt_templates;  -- expect 1
-- ================================================================
