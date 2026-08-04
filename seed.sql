-- ================================================================
--  ISO Validator — Seed Data
--  Database : iso_validator_db
--  Run AFTER schema.sql
--
--  Sections:
--    1.  system_config          (6 rows)
--    2.  ollama_config          (9 rows)
--    3.  users                  (3 rows: admin, analyst, viewer)
--    4.  switch_profiles        (2 rows: UAT + DEV)
--    5.  message_formats        (1 row: basic 0200 format for UAT Switch)
--    6.  message_format_versions(1 row: version 1 of the format above)
--    7.  validation_rules       (10 rows: DE rules for profile 1, MTI 0200)
--    8.  rule_allowed_values    (5 rows: DE3 processing codes)
--    9.  field_definitions      (15 rows: DE catalog for 0200 builder)
--    10. ai_prompt_templates    (1 row: GLOBAL template)
--    11. ai_prompt_template_versions (1 row: version 1)
-- ================================================================

USE iso_validator_db;

-- ================================================================
-- 1. SYSTEM CONFIG (6 rows)
--    Owned by: auth-service
--    config_type tells the service how to parse config_value.
--    is_sensitive=TRUE masks the value in API responses.
-- ================================================================

INSERT INTO system_config (config_key, config_value, config_type, description, is_sensitive, updated_by) VALUES
('jwt.expiry.minutes',      '60',                'INTEGER', 'JWT access token expiry in minutes',            FALSE, 'SYSTEM'),
('jwt.issuer',              'iso8583-validator', 'STRING',  'JWT iss claim value',                           FALSE, 'SYSTEM'),
('login.max.failures',      '5',                 'INTEGER', 'Max consecutive failed logins before lockout',  FALSE, 'SYSTEM'),
('account.lock.minutes',    '15',                'INTEGER', 'Account lock duration in minutes',              FALSE, 'SYSTEM'),
('login.session.max',       '3',                 'INTEGER', 'Max concurrent active sessions per user',       FALSE, 'SYSTEM'),
('pagination.default.size', '20',                'INTEGER', 'Default page size for all paginated list APIs', FALSE, 'SYSTEM');


-- ================================================================
-- 2. OLLAMA CONFIG (9 rows)
--    Owned by: ai-service
--    config_type drives runtime parsing.
--    is_sensitive masks value in GET /ai/config response.
-- ================================================================

INSERT INTO ollama_config (config_key, config_value, config_type, description, is_sensitive, updated_by) VALUES
('ollama.enabled',         'true',                    'BOOLEAN', 'Enable/disable Ollama AI integration globally',             FALSE, 'SYSTEM'),
('ollama.host',        'http://localhost:11434',  'STRING',  'Ollama API base URL',                                       FALSE, 'SYSTEM'),
('ollama.model',           'mistral:7b',              'STRING',  'Default model for explanations',                            FALSE, 'SYSTEM'),
('ollama.timeout.ms',      '20000',                   'INTEGER', 'HTTP request timeout in milliseconds',                      FALSE, 'SYSTEM'),
('ollama.max.tokens',      '10000',                     'INTEGER', 'Max tokens in Ollama response',                             FALSE, 'SYSTEM'),
('ollama.temperature',     '0.3',                     'DECIMAL', 'Model temperature (0.0=deterministic, 1.0=creative)',       FALSE, 'SYSTEM'),
('ollama.retry.count',     '2',                       'INTEGER', 'Number of retries on connection failure before fallback',   FALSE, 'SYSTEM'),
('ollama.fallback',        'SKIP_AI',                 'STRING',  'SKIP_AI = return result without AI; RETURN_ERROR = fail',   FALSE, 'SYSTEM'),
('ollama.log.prompts',     'false',                   'BOOLEAN', 'Log full prompts to ai_run_logs (disable in production)',   FALSE, 'SYSTEM');


-- ================================================================
-- 3. USERS (3 rows)
--    One per role: ADMIN, ANALYST, VIEWER
--
--    Passwords (BCrypt $2a$12$ rounds):
--      admin    → Admin@123
--      analyst  → Analyst@123
--      viewer   → Viewer@123
--
--    ⚠ DEV-only seed passwords.
--      Force password change on first login in UAT/PROD.
-- ================================================================

INSERT INTO users
    (username, password_hash, full_name, email, avatar_initials, role, active, created_by)
VALUES
(
    'admin',
    '$2a$12$6677N8WveOcQZS2IWDF9LOupWTvqunLpBXQQBtn7DMMQbHoNrIXSi',
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
    'Test Viewer',
    'viewer@iso8583.local',
    'TV',
    'VIEWER',
    TRUE,
    'SYSTEM'
);


-- ================================================================
-- 4. SWITCH PROFILES (2 rows)
--    UAT profile — active=TRUE, is_default=TRUE
--    DEV profile — active=TRUE, is_default=FALSE
--    host/port are placeholders — update before TCP test
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
--    total_fields=128 (standard secondary bitmap support)
--    status='active' — ready to use immediately
-- ================================================================

INSERT INTO message_formats
    (profile_id, format_name, iso_version, encoding, mti,
     total_fields, status, xml_content, checksum, current_version, created_by)
VALUES
(
    1,
    'ISO8583-1987 Base',
    'ISO 8583-1:1987',
    'ASCII',
    '0200',
    128,
    'active',
    '<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<!DOCTYPE isopackager PUBLIC
        "-//jPOS/jPOS Generic Packager DTD 1.0//EN"
        "http://jpos.org/dtd/generic-packager-1.0.dtd">

<!-- ISO 8583:1987 (ASCII) field descriptions for GenericPackager -->

<isopackager>
    <isofield
            id="0"
            length="4"
            name="MESSAGE TYPE INDICATOR"
            class="org.jpos.iso.IFA_NUMERIC"/>
    <isofield
            id="1"
            length="16"
            name="BIT MAP"
            class="org.jpos.iso.IFA_BITMAP"/>
    <isofield
            id="2"
            length="19"
            name="PAN - PRIMARY ACCOUNT NUMBER"
            class="org.jpos.iso.IFA_LLNUM"/>
    <isofield
            id="3"
            length="6"
            name="PROCESSING CODE"
            class="org.jpos.iso.IFA_NUMERIC"/>
    <isofield
            id="4"
            length="12"
            name="AMOUNT, TRANSACTION"
            class="org.jpos.iso.IFA_NUMERIC"/>
    <isofield
            id="5"
            length="12"
            name="AMOUNT, SETTLEMENT"
            class="org.jpos.iso.IFA_NUMERIC"/>
    <isofield
            id="6"
            length="12"
            name="AMOUNT, CARDHOLDER BILLING"
            class="org.jpos.iso.IFA_NUMERIC"/>
    <isofield
            id="7"
            length="10"
            name="TRANSMISSION DATE AND TIME"
            class="org.jpos.iso.IFA_NUMERIC"/>
    <isofield
            id="8"
            length="8"
            name="AMOUNT, CARDHOLDER BILLING FEE"
            class="org.jpos.iso.IFA_NUMERIC"/>
    <isofield
            id="9"
            length="8"
            name="CONVERSION RATE, SETTLEMENT"
            class="org.jpos.iso.IFA_NUMERIC"/>
    <isofield
            id="10"
            length="8"
            name="CONVERSION RATE, CARDHOLDER BILLING"
            class="org.jpos.iso.IFA_NUMERIC"/>
    <isofield
            id="11"
            length="6"
            name="SYSTEM TRACE AUDIT NUMBER"
            class="org.jpos.iso.IFA_NUMERIC"/>
    <isofield
            id="12"
            length="6"
            name="TIME, LOCAL TRANSACTION"
            class="org.jpos.iso.IFA_NUMERIC"/>
    <isofield
            id="13"
            length="4"
            name="DATE, LOCAL TRANSACTION"
            class="org.jpos.iso.IFA_NUMERIC"/>
    <isofield
            id="14"
            length="4"
            name="DATE, EXPIRATION"
            class="org.jpos.iso.IFA_NUMERIC"/>
    <isofield
            id="15"
            length="4"
            name="DATE, SETTLEMENT"
            class="org.jpos.iso.IFA_NUMERIC"/>
    <isofield
            id="16"
            length="4"
            name="DATE, CONVERSION"
            class="org.jpos.iso.IFA_NUMERIC"/>
    <isofield
            id="17"
            length="4"
            name="DATE, CAPTURE"
            class="org.jpos.iso.IFA_NUMERIC"/>
    <isofield
            id="18"
            length="4"
            name="MERCHANTS TYPE"
            class="org.jpos.iso.IFA_NUMERIC"/>
    <isofield
            id="19"
            length="3"
            name="ACQUIRING INSTITUTION COUNTRY CODE"
            class="org.jpos.iso.IFA_NUMERIC"/>
    <isofield
            id="20"
            length="3"
            name="PAN EXTENDED COUNTRY CODE"
            class="org.jpos.iso.IFA_NUMERIC"/>
    <isofield
            id="21"
            length="3"
            name="FORWARDING INSTITUTION COUNTRY CODE"
            class="org.jpos.iso.IFA_NUMERIC"/>
    <isofield
            id="22"
            length="3"
            name="POINT OF SERVICE ENTRY MODE"
            class="org.jpos.iso.IFA_NUMERIC"/>
    <isofield
            id="23"
            length="3"
            name="CARD SEQUENCE NUMBER"
            class="org.jpos.iso.IFA_NUMERIC"/>
    <isofield
            id="24"
            length="3"
            name="NETWORK INTERNATIONAL IDENTIFIEER"
            class="org.jpos.iso.IFA_NUMERIC"/>
    <isofield
            id="25"
            length="2"
            name="POINT OF SERVICE CONDITION CODE"
            class="org.jpos.iso.IFA_NUMERIC"/>
    <isofield
            id="26"
            length="2"
            name="POINT OF SERVICE PIN CAPTURE CODE"
            class="org.jpos.iso.IFA_NUMERIC"/>
    <isofield
            id="27"
            length="1"
            name="AUTHORIZATION IDENTIFICATION RESP LEN"
            class="org.jpos.iso.IFA_NUMERIC"/>
    <isofield
            id="28"
            length="9"
            name="AMOUNT, TRANSACTION FEE"
            class="org.jpos.iso.IFA_AMOUNT"/>
    <isofield
            id="29"
            length="9"
            name="AMOUNT, SETTLEMENT FEE"
            class="org.jpos.iso.IFA_AMOUNT"/>
    <isofield
            id="30"
            length="9"
            name="AMOUNT, TRANSACTION PROCESSING FEE"
            class="org.jpos.iso.IFA_AMOUNT"/>
    <isofield
            id="31"
            length="9"
            name="AMOUNT, SETTLEMENT PROCESSING FEE"
            class="org.jpos.iso.IFA_AMOUNT"/>
    <isofield
            id="32"
            length="11"
            name="ACQUIRING INSTITUTION IDENT CODE"
            class="org.jpos.iso.IFA_LLNUM"/>
    <isofield
            id="33"
            length="11"
            name="FORWARDING INSTITUTION IDENT CODE"
            class="org.jpos.iso.IFA_LLNUM"/>
    <isofield
            id="34"
            length="28"
            name="PAN EXTENDED"
            class="org.jpos.iso.IFA_LLCHAR"/>
    <isofield
            id="35"
            length="37"
            name="TRACK 2 DATA"
            class="org.jpos.iso.IFA_LLNUM"/>
    <isofield
            id="36"
            length="104"
            name="TRACK 3 DATA"
            class="org.jpos.iso.IFA_LLLCHAR"/>
    <isofield
            id="37"
            length="12"
            name="RETRIEVAL REFERENCE NUMBER"
            class="org.jpos.iso.IF_CHAR"/>
    <isofield
            id="38"
            length="6"
            name="AUTHORIZATION IDENTIFICATION RESPONSE"
            class="org.jpos.iso.IF_CHAR"/>
    <isofield
            id="39"
            length="2"
            name="RESPONSE CODE"
            class="org.jpos.iso.IF_CHAR"/>
    <isofield
            id="40"
            length="3"
            name="SERVICE RESTRICTION CODE"
            class="org.jpos.iso.IF_CHAR"/>
    <isofield
            id="41"
            length="8"
            name="CARD ACCEPTOR TERMINAL IDENTIFICACION"
            class="org.jpos.iso.IF_CHAR"/>
    <isofield
            id="42"
            length="15"
            name="CARD ACCEPTOR IDENTIFICATION CODE"
            class="org.jpos.iso.IF_CHAR"/>
    <isofield
            id="43"
            length="40"
            name="CARD ACCEPTOR NAME/LOCATION"
            class="org.jpos.iso.IF_CHAR"/>
    <isofield
            id="44"
            length="25"
            name="ADITIONAL RESPONSE DATA"
            class="org.jpos.iso.IFA_LLCHAR"/>
    <isofield
            id="45"
            length="76"
            name="TRACK 1 DATA"
            class="org.jpos.iso.IFA_LLCHAR"/>
    <isofield
            id="46"
            length="999"
            name="ADITIONAL DATA - ISO"
            class="org.jpos.iso.IFA_LLLCHAR"/>
    <isofield
            id="47"
            length="999"
            name="ADITIONAL DATA - NATIONAL"
            class="org.jpos.iso.IFA_LLLCHAR"/>
    <isofield
            id="48"
            length="999"
            name="ADITIONAL DATA - PRIVATE"
            class="org.jpos.iso.IFA_LLLCHAR"/>
    <isofield
            id="49"
            length="3"
            name="CURRENCY CODE, TRANSACTION"
            class="org.jpos.iso.IF_CHAR"/>
    <isofield
            id="50"
            length="3"
            name="CURRENCY CODE, SETTLEMENT"
            class="org.jpos.iso.IF_CHAR"/>
    <isofield
            id="51"
            length="3"
            name="CURRENCY CODE, CARDHOLDER BILLING"
            class="org.jpos.iso.IF_CHAR"/>
    <isofield
            id="52"
            length="8"
            name="PIN DATA"
            class="org.jpos.iso.IFA_BINARY"/>
    <isofield
            id="53"
            length="16"
            name="SECURITY RELATED CONTROL INFORMATION"
            class="org.jpos.iso.IFA_NUMERIC"/>
    <isofield
            id="54"
            length="120"
            name="ADDITIONAL AMOUNTS"
            class="org.jpos.iso.IFA_LLLCHAR"/>
    <isofield
            id="55"
            length="999"
            name="RESERVED ISO"
            class="org.jpos.iso.IFA_LLLCHAR"/>
    <isofield
            id="56"
            length="999"
            name="RESERVED ISO"
            class="org.jpos.iso.IFA_LLLCHAR"/>
    <isofield
            id="57"
            length="999"
            name="RESERVED NATIONAL"
            class="org.jpos.iso.IFA_LLLCHAR"/>
    <isofield
            id="58"
            length="999"
            name="RESERVED NATIONAL"
            class="org.jpos.iso.IFA_LLLCHAR"/>
    <isofield
            id="59"
            length="999"
            name="RESERVED NATIONAL"
            class="org.jpos.iso.IFA_LLLCHAR"/>
    <isofield
            id="60"
            length="999"
            name="RESERVED PRIVATE"
            class="org.jpos.iso.IFA_LLLCHAR"/>
    <isofield
            id="61"
            length="999"
            name="RESERVED PRIVATE"
            class="org.jpos.iso.IFA_LLLCHAR"/>
    <isofield
            id="62"
            length="999"
            name="RESERVED PRIVATE"
            class="org.jpos.iso.IFA_LLLCHAR"/>
    <isofield
            id="63"
            length="999"
            name="RESERVED PRIVATE"
            class="org.jpos.iso.IFA_LLLCHAR"/>
    <isofield
            id="64"
            length="8"
            name="MESSAGE AUTHENTICATION CODE FIELD"
            class="org.jpos.iso.IFA_BINARY"/>
    <isofield
            id="65"
            length="1"
            name="BITMAP, EXTENDED"
            class="org.jpos.iso.IFA_BINARY"/>
    <isofield
            id="66"
            length="1"
            name="SETTLEMENT CODE"
            class="org.jpos.iso.IFA_NUMERIC"/>
    <isofield
            id="67"
            length="2"
            name="EXTENDED PAYMENT CODE"
            class="org.jpos.iso.IFA_NUMERIC"/>
    <isofield
            id="68"
            length="3"
            name="RECEIVING INSTITUTION COUNTRY CODE"
            class="org.jpos.iso.IFA_NUMERIC"/>
    <isofield
            id="69"
            length="3"
            name="SETTLEMENT INSTITUTION COUNTRY CODE"
            class="org.jpos.iso.IFA_NUMERIC"/>
    <isofield
            id="70"
            length="3"
            name="NETWORK MANAGEMENT INFORMATION CODE"
            class="org.jpos.iso.IFA_NUMERIC"/>
    <isofield
            id="71"
            length="4"
            name="MESSAGE NUMBER"
            class="org.jpos.iso.IFA_NUMERIC"/>
    <isofield
            id="72"
            length="4"
            name="MESSAGE NUMBER LAST"
            class="org.jpos.iso.IFA_NUMERIC"/>
    <isofield
            id="73"
            length="6"
            name="DATE ACTION"
            class="org.jpos.iso.IFA_NUMERIC"/>
    <isofield
            id="74"
            length="10"
            name="CREDITS NUMBER"
            class="org.jpos.iso.IFA_NUMERIC"/>
    <isofield
            id="75"
            length="10"
            name="CREDITS REVERSAL NUMBER"
            class="org.jpos.iso.IFA_NUMERIC"/>
    <isofield
            id="76"
            length="10"
            name="DEBITS NUMBER"
            class="org.jpos.iso.IFA_NUMERIC"/>
    <isofield
            id="77"
            length="10"
            name="DEBITS REVERSAL NUMBER"
            class="org.jpos.iso.IFA_NUMERIC"/>
    <isofield
            id="78"
            length="10"
            name="TRANSFER NUMBER"
            class="org.jpos.iso.IFA_NUMERIC"/>
    <isofield
            id="79"
            length="10"
            name="TRANSFER REVERSAL NUMBER"
            class="org.jpos.iso.IFA_NUMERIC"/>
    <isofield
            id="80"
            length="10"
            name="INQUIRIES NUMBER"
            class="org.jpos.iso.IFA_NUMERIC"/>
    <isofield
            id="81"
            length="10"
            name="AUTHORIZATION NUMBER"
            class="org.jpos.iso.IFA_NUMERIC"/>
    <isofield
            id="82"
            length="12"
            name="CREDITS, PROCESSING FEE AMOUNT"
            class="org.jpos.iso.IFA_NUMERIC"/>
    <isofield
            id="83"
            length="12"
            name="CREDITS, TRANSACTION FEE AMOUNT"
            class="org.jpos.iso.IFA_NUMERIC"/>
    <isofield
            id="84"
            length="12"
            name="DEBITS, PROCESSING FEE AMOUNT"
            class="org.jpos.iso.IFA_NUMERIC"/>
    <isofield
            id="85"
            length="12"
            name="DEBITS, TRANSACTION FEE AMOUNT"
            class="org.jpos.iso.IFA_NUMERIC"/>
    <isofield
            id="86"
            length="16"
            name="CREDITS, AMOUNT"
            class="org.jpos.iso.IFA_NUMERIC"/>
    <isofield
            id="87"
            length="16"
            name="CREDITS, REVERSAL AMOUNT"
            class="org.jpos.iso.IFA_NUMERIC"/>
    <isofield
            id="88"
            length="16"
            name="DEBITS, AMOUNT"
            class="org.jpos.iso.IFA_NUMERIC"/>
    <isofield
            id="89"
            length="16"
            name="DEBITS, REVERSAL AMOUNT"
            class="org.jpos.iso.IFA_NUMERIC"/>
    <isofield
            id="90"
            length="42"
            name="ORIGINAL DATA ELEMENTS"
            class="org.jpos.iso.IFA_NUMERIC"/>
    <isofield
            id="91"
            length="1"
            name="FILE UPDATE CODE"
            class="org.jpos.iso.IF_CHAR"/>
    <isofield
            id="92"
            length="2"
            name="FILE SECURITY CODE"
            class="org.jpos.iso.IF_CHAR"/>
    <isofield
            id="93"
            length="6"
            name="RESPONSE INDICATOR"
            class="org.jpos.iso.IF_CHAR"/>
    <isofield
            id="94"
            length="7"
            name="SERVICE INDICATOR"
            class="org.jpos.iso.IF_CHAR"/>
    <isofield
            id="95"
            length="42"
            name="REPLACEMENT AMOUNTS"
            class="org.jpos.iso.IF_CHAR"/>
    <isofield
            id="96"
            length="16"
            name="MESSAGE SECURITY CODE"
            class="org.jpos.iso.IFA_BINARY"/>
    <isofield
            id="97"
            length="17"
            name="AMOUNT, NET SETTLEMENT"
            class="org.jpos.iso.IFA_AMOUNT"/>
    <isofield
            id="98"
            length="25"
            name="PAYEE"
            class="org.jpos.iso.IF_CHAR"/>
    <isofield
            id="99"
            length="11"
            name="SETTLEMENT INSTITUTION IDENT CODE"
            class="org.jpos.iso.IFA_LLNUM"/>
    <isofield
            id="100"
            length="11"
            name="RECEIVING INSTITUTION IDENT CODE"
            class="org.jpos.iso.IFA_LLNUM"/>
    <isofield
            id="101"
            length="17"
            name="FILE NAME"
            class="org.jpos.iso.IFA_LLCHAR"/>
    <isofield
            id="102"
            length="28"
            name="ACCOUNT IDENTIFICATION 1"
            class="org.jpos.iso.IFA_LLCHAR"/>
    <isofield
            id="103"
            length="28"
            name="ACCOUNT IDENTIFICATION 2"
            class="org.jpos.iso.IFA_LLCHAR"/>
    <isofield
            id="104"
            length="100"
            name="TRANSACTION DESCRIPTION"
            class="org.jpos.iso.IFA_LLLCHAR"/>
    <isofield
            id="105"
            length="999"
            name="RESERVED ISO USE"
            class="org.jpos.iso.IFA_LLLCHAR"/>
    <isofield
            id="106"
            length="999"
            name="RESERVED ISO USE"
            class="org.jpos.iso.IFA_LLLCHAR"/>
    <isofield
            id="107"
            length="999"
            name="RESERVED ISO USE"
            class="org.jpos.iso.IFA_LLLCHAR"/>
    <isofield
            id="108"
            length="999"
            name="RESERVED ISO USE"
            class="org.jpos.iso.IFA_LLLCHAR"/>
    <isofield
            id="109"
            length="999"
            name="RESERVED ISO USE"
            class="org.jpos.iso.IFA_LLLCHAR"/>
    <isofield
            id="110"
            length="999"
            name="RESERVED ISO USE"
            class="org.jpos.iso.IFA_LLLCHAR"/>
    <isofield
            id="111"
            length="999"
            name="RESERVED ISO USE"
            class="org.jpos.iso.IFA_LLLCHAR"/>
    <isofield
            id="112"
            length="999"
            name="RESERVED NATIONAL USE"
            class="org.jpos.iso.IFA_LLLCHAR"/>
    <isofield
            id="113"
            length="999"
            name="RESERVED NATIONAL USE"
            class="org.jpos.iso.IFA_LLLCHAR"/>
    <isofield
            id="114"
            length="999"
            name="RESERVED NATIONAL USE"
            class="org.jpos.iso.IFA_LLLCHAR"/>
    <isofield
            id="115"
            length="999"
            name="RESERVED NATIONAL USE"
            class="org.jpos.iso.IFA_LLLCHAR"/>
    <isofield
            id="116"
            length="999"
            name="RESERVED NATIONAL USE"
            class="org.jpos.iso.IFA_LLLCHAR"/>
    <isofield
            id="117"
            length="999"
            name="RESERVED NATIONAL USE"
            class="org.jpos.iso.IFA_LLLCHAR"/>
    <isofield
            id="118"
            length="999"
            name="RESERVED NATIONAL USE"
            class="org.jpos.iso.IFA_LLLCHAR"/>
    <isofield
            id="119"
            length="999"
            name="RESERVED NATIONAL USE"
            class="org.jpos.iso.IFA_LLLCHAR"/>
    <isofield
            id="120"
            length="999"
            name="RESERVED PRIVATE USE"
            class="org.jpos.iso.IFA_LLLCHAR"/>
    <isofield
            id="121"
            length="999"
            name="RESERVED PRIVATE USE"
            class="org.jpos.iso.IFA_LLLCHAR"/>
    <isofield
            id="122"
            length="999"
            name="RESERVED PRIVATE USE"
            class="org.jpos.iso.IFA_LLLCHAR"/>
    <isofield
            id="123"
            length="999"
            name="RESERVED PRIVATE USE"
            class="org.jpos.iso.IFA_LLLCHAR"/>
    <isofield
            id="124"
            length="999"
            name="RESERVED PRIVATE USE"
            class="org.jpos.iso.IFA_LLLCHAR"/>
    <isofield
            id="125"
            length="999"
            name="RESERVED PRIVATE USE"
            class="org.jpos.iso.IFA_LLLCHAR"/>
    <isofield
            id="126"
            length="999"
            name="RESERVED PRIVATE USE"
            class="org.jpos.iso.IFA_LLLCHAR"/>
    <isofield
            id="127"
            length="999"
            name="RESERVED PRIVATE USE"
            class="org.jpos.iso.IFA_LLLCHAR"/>
    <isofield
            id="128"
            length="8"
            name="MAC 2"
            class="org.jpos.iso.IFA_BINARY"/>
</isopackager>',
    SHA2('iso8583-1987-base-v1', 256),
    1,
    'admin'
);


-- ================================================================
-- 6. MESSAGE FORMAT VERSIONS (1 row)
--    Version 1 for the format inserted above.
--    is_current=TRUE — this is the active version.
--    validated_ok=TRUE — assumed valid for seed; engine will
--    verify on first load and update this if needed.
-- ================================================================

SET @format_id = LAST_INSERT_ID();

INSERT INTO message_format_versions
    (format_id, version_number, xml_content, checksum, change_note,
     is_current, validated_ok, validated_at, created_by)
SELECT
    @format_id,
    1,
    xml_content,
    checksum,
    'Initial seed version',
    TRUE,
    TRUE,
    NOW(),
    'admin'
FROM message_formats
WHERE id = @format_id;


-- ================================================================
-- 7. VALIDATION RULES (10 rows)
--    All for profile_id=1 (UAT Switch), MTI=0200
--    Covers the most common DE checks for an authorization request
-- ================================================================

INSERT INTO validation_rules
    (profile_id, profile_name, mti, de_number, field_name,
     is_mandatory, min_length, max_length, data_type,
     pattern_regex, severity, priority, active, created_by)
VALUES
-- DE2 — PAN: mandatory, 13–19 digits
(1, 'UAT Switch', '0200', 'DE2',  'Primary Account Number',
 TRUE,  13, 19, 'numeric',      '^[0-9]{13,19}$',
 'CRITICAL', 1, TRUE, 'admin'),

-- DE3 — Processing Code: mandatory, exactly 6 digits
(1, 'UAT Switch', '0200', 'DE3',  'Processing Code',
 TRUE,  6,  6,  'numeric',      '^[0-9]{6}$',
 'CRITICAL', 2, TRUE, 'admin'),

-- DE4 — Amount: mandatory, exactly 12 digits
(1, 'UAT Switch', '0200', 'DE4',  'Amount Transaction',
 TRUE,  12, 12, 'numeric',      '^[0-9]{12}$',
 'CRITICAL', 3, TRUE, 'admin'),

-- DE7 — Transmission date/time: mandatory, exactly 10 digits (MMDDhhmmss)
(1, 'UAT Switch', '0200', 'DE7',  'Transmission Date and Time',
 TRUE,  10, 10, 'numeric',
 '^(0[1-9]|1[0-2])(0[1-9]|[12][0-9]|3[01])[0-2][0-9][0-5][0-9][0-5][0-9]$',
 'CRITICAL', 4, TRUE, 'admin'),

-- DE11 — STAN: mandatory, exactly 6 digits
(1, 'UAT Switch', '0200', 'DE11', 'System Trace Audit Number',
 TRUE,  6,  6,  'numeric',      '^[0-9]{6}$',
 'CRITICAL', 5, TRUE, 'admin'),

-- DE14 — Expiry date: optional, exactly 4 digits (YYMM)
(1, 'UAT Switch', '0200', 'DE14', 'Expiration Date',
 FALSE, 4,  4,  'numeric',      '^[0-9]{2}(0[1-9]|1[0-2])$',
 'WARNING', 6, TRUE, 'admin'),

-- DE22 — POS Entry Mode: mandatory, 3 digits
(1, 'UAT Switch', '0200', 'DE22', 'POS Entry Mode',
 TRUE,  3,  3,  'numeric',      '^[0-9]{3}$',
 'CRITICAL', 7, TRUE, 'admin'),

-- DE37 — RRN: mandatory, exactly 12 alphanumeric
(1, 'UAT Switch', '0200', 'DE37', 'Retrieval Reference Number',
 TRUE,  12, 12, 'alphanumeric', '^[A-Za-z0-9]{12}$',
 'CRITICAL', 8, TRUE, 'admin'),

-- DE41 — Terminal ID: mandatory, 8 chars
(1, 'UAT Switch', '0200', 'DE41', 'Card Acceptor Terminal ID',
 TRUE,  8,  8,  'alphanumeric', '^[A-Za-z0-9 ]{8}$',
 'CRITICAL', 9, TRUE, 'admin'),

-- DE49 — Currency Code: mandatory, exactly 3 digits (ISO 4217)
(1, 'UAT Switch', '0200', 'DE49', 'Currency Code Transaction',
 TRUE,  3,  3,  'numeric',      '^[0-9]{3}$',
 'CRITICAL', 10, TRUE, 'admin');


-- ================================================================
-- 8. RULE ALLOWED VALUES (5 rows)
--    DE3 Processing Code — common transaction types
-- ================================================================

SET @de3_rule_id = (
    SELECT id FROM validation_rules
    WHERE profile_id = 1 AND mti = '0200' AND de_number = 'DE3'
    LIMIT 1
);

INSERT INTO rule_allowed_values (rule_id, allowed_value, value_label, sort_order, created_by) VALUES
(@de3_rule_id, '000000', 'Purchase',             1, 'admin'),
(@de3_rule_id, '010000', 'Cash Withdrawal',       2, 'admin'),
(@de3_rule_id, '200000', 'Refund / Return',        3, 'admin'),
(@de3_rule_id, '280000', 'Balance Inquiry',        4, 'admin'),
(@de3_rule_id, '310000', 'Balance Inquiry (ATM)',  5, 'admin');


-- ================================================================
-- 9. FIELD DEFINITIONS (15 rows)
--    For profile_id=1 (UAT Switch), MTI=0200
--    profile_name snapshot included per blueprint requirement.
--    active=TRUE for all — ready to use in message builder.
-- ================================================================

INSERT INTO field_definitions
    (profile_id, profile_name, mti, de_number, field_name,
     data_type, max_length, is_llvar, is_lllvar, is_mandatory,
     placeholder_value, display_order, is_builder_visible,
     active, description, created_by)
VALUES
-- MTI — hidden from builder (handled by format/profile selection)
(1, 'UAT Switch', '0200', 'MTI',  'Message Type Indicator',
 'numeric',      4,   FALSE, FALSE, TRUE,  '0200',            0,  FALSE, TRUE,
 'ISO 8583 Message Type Indicator', 'admin'),

-- DE1 — bitmap, hidden from builder
(1, 'UAT Switch', '0200', 'DE1',  'Primary Bitmap',
 'binary',       8,   FALSE, FALSE, TRUE,  NULL,              0,  FALSE, TRUE,
 'Auto-generated primary bitmap — not user-editable', 'admin'),

-- Visible mandatory fields
(1, 'UAT Switch', '0200', 'DE2',  'Primary Account Number (PAN)',
 'numeric',      19,  TRUE,  FALSE, TRUE,  '4111111111111111', 1,  TRUE, TRUE,
 'Card number — masked in display and storage', 'admin'),

(1, 'UAT Switch', '0200', 'DE3',  'Processing Code',
 'numeric',      6,   FALSE, FALSE, TRUE,  '000000',           2,  TRUE, TRUE,
 'First 2 digits = transaction type e.g. 00=Purchase', 'admin'),

(1, 'UAT Switch', '0200', 'DE4',  'Transaction Amount',
 'numeric',      12,  FALSE, FALSE, TRUE,  '000000010000',     3,  TRUE, TRUE,
 'Amount in minor units e.g. 000000010000 = ₹100.00', 'admin'),

(1, 'UAT Switch', '0200', 'DE7',  'Transmission Date and Time',
 'numeric',      10,  FALSE, FALSE, TRUE,  '0514143000',       4,  TRUE, TRUE,
 'MMDDhhmmss format', 'admin'),

(1, 'UAT Switch', '0200', 'DE11', 'System Trace Audit Number',
 'numeric',      6,   FALSE, FALSE, TRUE,  '000001',           5,  TRUE, TRUE,
 'Unique per transaction per day', 'admin'),

(1, 'UAT Switch', '0200', 'DE12', 'Local Transaction Time',
 'numeric',      6,   FALSE, FALSE, FALSE, '143000',           6,  TRUE, TRUE,
 'hhmmss format', 'admin'),

(1, 'UAT Switch', '0200', 'DE13', 'Local Transaction Date',
 'numeric',      4,   FALSE, FALSE, FALSE, '0514',             7,  TRUE, TRUE,
 'MMDD format', 'admin'),

(1, 'UAT Switch', '0200', 'DE14', 'Expiration Date',
 'numeric',      4,   FALSE, FALSE, FALSE, '2612',             8,  TRUE, TRUE,
 'YYMM format — e.g. 2612 = Dec 2026', 'admin'),

(1, 'UAT Switch', '0200', 'DE22', 'POS Entry Mode',
 'numeric',      3,   FALSE, FALSE, TRUE,  '051',              9,  TRUE, TRUE,
 '051=chip+PIN, 071=contactless, 011=manual', 'admin'),

(1, 'UAT Switch', '0200', 'DE37', 'Retrieval Reference Number',
 'alphanumeric', 12,  FALSE, FALSE, TRUE,  'RRN000000001',     10, TRUE, TRUE,
 '12 alphanumeric — must be unique per transaction', 'admin'),

(1, 'UAT Switch', '0200', 'DE41', 'Card Acceptor Terminal ID',
 'alphanumeric', 8,   FALSE, FALSE, TRUE,  'TERM0001',         11, TRUE, TRUE,
 '8-char terminal identifier', 'admin'),

(1, 'UAT Switch', '0200', 'DE42', 'Card Acceptor ID Code (Merchant ID)',
 'alphanumeric', 15,  FALSE, FALSE, FALSE, 'MERCHANT000001',   12, TRUE, TRUE,
 '15-char merchant identifier', 'admin'),

(1, 'UAT Switch', '0200', 'DE49', 'Currency Code Transaction',
 'numeric',      3,   FALSE, FALSE, TRUE,  '356',              13, TRUE, TRUE,
 'ISO 4217 — 356=INR, 840=USD, 978=EUR', 'admin');


-- ================================================================
-- 10. AI PROMPT TEMPLATES (1 row — GLOBAL scope)
--     Used by all profiles unless a PROFILE-scope override exists.
--     Variables: {mti} {profile} {errors} {fields}
-- ================================================================

INSERT INTO ai_prompt_templates
    (template_name, scope, profile_id, profile_name, prompt_template,
     variables_used, current_version, active, created_by)
VALUES
(
    'Global ISO 8583 Validation Explainer',
    'GLOBAL',
    NULL,
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
-- 11. AI PROMPT TEMPLATE VERSIONS (1 row)
--     Version 1 of the global template above.
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
-- SEED VERIFICATION QUERIES
-- Run after loading to confirm row counts:
--
-- SELECT 'system_config'              AS tbl, COUNT(*) AS cnt FROM system_config
-- UNION ALL
-- SELECT 'ollama_config',                      COUNT(*)        FROM ollama_config
-- UNION ALL
-- SELECT 'users',                              COUNT(*)        FROM users
-- UNION ALL
-- SELECT 'switch_profiles',                    COUNT(*)        FROM switch_profiles
-- UNION ALL
-- SELECT 'message_formats',                    COUNT(*)        FROM message_formats
-- UNION ALL
-- SELECT 'message_format_versions',            COUNT(*)        FROM message_format_versions
-- UNION ALL
-- SELECT 'validation_rules',                   COUNT(*)        FROM validation_rules
-- UNION ALL
-- SELECT 'rule_allowed_values',                COUNT(*)        FROM rule_allowed_values
-- UNION ALL
-- SELECT 'field_definitions',                  COUNT(*)        FROM field_definitions
-- UNION ALL
-- SELECT 'ai_prompt_templates',                COUNT(*)        FROM ai_prompt_templates
-- UNION ALL
-- SELECT 'ai_prompt_template_versions',        COUNT(*)        FROM ai_prompt_template_versions;
--
-- Expected: 6, 9, 3, 2, 1, 1, 10, 5, 15, 1, 1
-- ================================================================