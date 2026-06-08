# ISO 8583 Validator Backend — Janani Platform

## Prerequisites

| Tool | Version | Notes |
|------|---------|-------|
| Java | 21 | JAVA_HOME must be set |
| Maven | 3.9+ | or use bundled `./mvnw` |
| Docker + Docker Compose | 24+ | for infrastructure |
| Git | any | |

## 1. Start Infrastructure

```bash
docker-compose up -d
```

Wait for all containers to be healthy (~30s):

```bash
docker-compose ps          # all should show "healthy"
```

Verify:
- MySQL: `mysql -h 127.0.0.1 -uroot -proot iso_validator_db -e "SHOW TABLES;"`
- RabbitMQ UI: http://localhost:15672 (guest / guest)
- Redis: `redis-cli ping` → PONG

## 2. Install common-lib

common-lib is a shared dependency — must be built first:

```bash
cd common-lib && ./mvnw clean install -DskipTests && cd ..
```

## 3. Start Spring Boot Services

Start in this exact order (each waits ~10s before the next):

```bash
# 1. Service Registry (Eureka)
cd service-registry
./mvnw spring-boot:run &
sleep 15

# 2. Config Server
cd ../config-server
./mvnw spring-boot:run &
sleep 10

# 3. Auth Service
cd ../auth-service
./mvnw spring-boot:run &
sleep 10

# 4. Profile Service
cd ../profile-service
./mvnw spring-boot:run &
sleep 10

# 5. Rules Service
cd ../rules-service
./mvnw spring-boot:run &
sleep 10

# 6. AI Service
cd ../ai-service
./mvnw spring-boot:run &
sleep 10

# 7. Validation Engine
cd ../validation-engine
./mvnw spring-boot:run &
sleep 10

# 8. History Service
cd ../history-service
./mvnw spring-boot:run &
sleep 10

# 9. API Gateway (last — routes all traffic)
cd ../api-gateway
./mvnw spring-boot:run &
```

Or use the provided `start-all.sh` script.

## 4. Verify All Services Are Up

```bash
./health-check.sh
```

Eureka Dashboard: http://localhost:8761 — all 7 application services should appear.

## 5. Service Ports

| Service | Port | URL |
|---------|------|-----|
| Eureka | 8761 | http://localhost:8761 |
| Config Server | 8888 | http://localhost:8888/actuator/health |
| API Gateway | 8080 | http://localhost:8080/actuator/health |
| Auth Service | 8081 | http://localhost:8081/swagger-ui.html |
| Profile Service | 8082 | http://localhost:8082/swagger-ui.html |
| Rules Service | 8083 | http://localhost:8083/swagger-ui.html |
| Validation Engine | 8084 | http://localhost:8084/swagger-ui.html |
| AI Service | 8085 | http://localhost:8085/swagger-ui.html |
| History Service | 8086 | http://localhost:8086/swagger-ui.html |

## 6. First Request Walkthrough

### Step 1 — Login (get JWT)
```bash
curl -s -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"Admin@123"}' | jq .
```
Copy the `data.token` value.

### Step 2 — Create a profile
```bash
TOKEN="<paste token here>"
curl -s -X POST http://localhost:8080/profiles \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"profileName":"UAT Switch","description":"Test profile"}' | jq .
```

### Step 3 — Create a format for the profile
```bash
PROFILE_ID=1
curl -s -X POST http://localhost:8080/formats \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{
    \"profileId\": $PROFILE_ID,
    \"formatName\": \"ISO87 ASCII\",
    \"mti\": \"0200\",
    \"xmlContent\": \"<?xml version=\\\"1.0\\\" encoding=\\\"UTF-8\\\"?><isopackager><isofield id=\\\"0\\\" length=\\\"4\\\" name=\\\"MTI\\\" class=\\\"org.jpos.iso.IFA_NUMERIC\\\"/><isofield id=\\\"1\\\" length=\\\"8\\\" name=\\\"Bitmap\\\" class=\\\"org.jpos.iso.IFA_BITMAP\\\"/><isofield id=\\\"2\\\" length=\\\"19\\\" name=\\\"PAN\\\" class=\\\"org.jpos.iso.IFA_LLNUM\\\"/><isofield id=\\\"4\\\" length=\\\"12\\\" name=\\\"Amount\\\" class=\\\"org.jpos.iso.IFA_NUMERIC\\\"/></isopackager>\"
  }" | jq .
```

### Step 4 — Validate an ISO 8583 message
```bash
curl -s -X POST http://localhost:8080/validate \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"profileId\": $PROFILE_ID, \"rawMessage\": \"02004000000000000000000000010000\", \"enableAi\": false}" | jq .
```
Note the `data.runReference` (e.g. `VLD-20250514-00001`).

### Step 5 — View in history
```bash
RUN_REF="VLD-20250514-00001"
curl -s http://localhost:8080/history/runs/$RUN_REF \
  -H "Authorization: Bearer $TOKEN" | jq .
```

### Step 6 — View audit logs
```bash
curl -s "http://localhost:8080/audit/logs?page=0&size=5" \
  -H "Authorization: Bearer $TOKEN" | jq .
```

## 7. Postman Collection

Import `ISO_8583_Validator_Full_Coverage.postman_collection.json`.

**Before running:**
1. Set environment variable `baseUrl = http://localhost:8080`
2. Run **01 Login** first — JWT auto-saves to `{{authToken}}`
3. For AI template creation, use field names matching the entity:
```json
   {
     "templateName": "Global ISO Explainer",
     "scope": "GLOBAL",
     "promptTemplate": "Analyze these errors for MTI {mti}: {errors}",
     "variablesUsed": "[\"{mti}\",\"{errors}\"]"
   }
```
*(The Postman collection uses `templateScope`/`templateContent` which are the architecture doc names; the actual entity/API uses `scope`/`promptTemplate`)*

## 8. Bug Fix Notes

| # | What Was Fixed |
|---|---------------|
| BUG 1 | Already fixed — `ValidationController` correctly maps to `/validate` |
| BUG 2 | `UpdateUserRequest` created; `PUT /users/{id}` no longer accepts password |
| BUG 3 | Internal auth endpoint moved to `InternalAuthController` → correct path `/internal/auth/validate-token` |
| + | `@EnableMethodSecurity` + `HeaderAuthenticationFilter` added to auth-service — `@PreAuthorize` now enforced |
| + | History-service package `history_service` → `history` (compilation fix) |
| + | `AuditLogEvent` now has nested `Payload` — audit data actually stored in DB |
| + | `CacheInvalidationEvent` now reads `payload.type` — cache invalidation actually fires |
| + | `ValidationRunEvent` flattened — timing, counts, AI stored correctly in history |
| + | RabbitMQ routing key unified to `validation.run` across all services |
| + | `@Pattern` on `DataType` enum removed (validation error on startup) |
| GAP 4 | OpenAPI/Swagger added to all services |
| GAP 5 | `GET /users` is now paginated |
| GAP 6 | History endpoint is `/history/runs` and `/history/runs/{runRef}` |

## 9. DB Credentials

All services use `root:root` on `iso_validator_db`. To connect:
```bash
mysql -h 127.0.0.1 -P 3306 -uroot -proot iso_validator_db
```

## 10. Ollama Setup (for AI features)

```bash
# Option A — via Docker (see docker-compose.yml ollama service)
docker exec iso-ollama ollama pull mistral:7b

# Option B — native install
ollama serve &
ollama pull mistral:7b
```

Then enable AI in requests: `"enableAi": true`.