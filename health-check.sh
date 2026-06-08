#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
#  ISO 8583 Validator — Health Check Script
#  Usage: chmod +x health-check.sh && ./health-check.sh
# ─────────────────────────────────────────────────────────────────────────────

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'

ok()   { echo -e "${GREEN}  ✓${NC}  $1"; }
fail() { echo -e "${RED}  ✗${NC}  $1"; FAILED=$((FAILED+1)); }
info() { echo -e "${YELLOW}  →${NC}  $1"; }

FAILED=0

check_http() {
  local name="$1" url="$2"
  STATUS=$(curl -s -o /dev/null -w "%{http_code}" --max-time 3 "$url" 2>/dev/null)
  if [[ "$STATUS" == "200" || "$STATUS" == "401" ]]; then
    ok "$name ($url) — HTTP $STATUS"
  else
    fail "$name ($url) — HTTP $STATUS (expected 200)"
  fi
}

check_tcp() {
  local name="$1" host="$2" port="$3"
  if nc -z -w2 "$host" "$port" 2>/dev/null; then
    ok "$name — $host:$port reachable"
  else
    fail "$name — $host:$port NOT reachable"
  fi
}

echo ""
echo "═══════════════════════════════════════════════"
echo "  ISO 8583 Validator — Health Check"
echo "═══════════════════════════════════════════════"

echo ""
echo "── Infrastructure ──────────────────────────────"
check_tcp  "MySQL"     "127.0.0.1" 3306
check_tcp  "RabbitMQ"  "127.0.0.1" 5672
check_http "RabbitMQ Management UI" "http://localhost:15672"
check_tcp  "Redis"     "127.0.0.1" 6379
check_tcp  "Ollama"    "127.0.0.1" 11434 && \
  check_http "Ollama API" "http://localhost:11434/api/tags" || true

echo ""
echo "── Spring Boot Services ─────────────────────────"
check_http "Service Registry (Eureka)" "http://localhost:8761/actuator/health"
check_http "Config Server"             "http://localhost:8888/actuator/health"
check_http "API Gateway"               "http://localhost:8080/actuator/health"
check_http "Auth Service"              "http://localhost:8081/actuator/health"
check_http "Profile Service"           "http://localhost:8082/actuator/health"
check_http "Rules Service"             "http://localhost:8083/actuator/health"
check_http "Validation Engine"         "http://localhost:8084/actuator/health"
check_http "AI Service"                "http://localhost:8085/actuator/health"
check_http "History Service"           "http://localhost:8086/actuator/health"

echo ""
echo "── Eureka Registrations ─────────────────────────"
EUREKA=$(curl -s --max-time 3 \
  -H "Accept: application/json" \
  "http://localhost:8761/eureka/apps" 2>/dev/null)

for svc in AUTH-SERVICE PROFILE-SERVICE RULES-SERVICE \
           VALIDATION-ENGINE AI-SERVICE HISTORY-SERVICE API-GATEWAY; do
  if echo "$EUREKA" | grep -qi "\"$svc\""; then
    ok "$svc registered in Eureka"
  else
    fail "$svc NOT found in Eureka"
  fi
done

echo ""
echo "── Swagger UI ───────────────────────────────────"
check_http "Auth Service Swagger"       "http://localhost:8081/swagger-ui.html"
check_http "Profile Service Swagger"    "http://localhost:8082/swagger-ui.html"
check_http "Rules Service Swagger"      "http://localhost:8083/swagger-ui.html"
check_http "Validation Engine Swagger"  "http://localhost:8084/swagger-ui.html"
check_http "AI Service Swagger"         "http://localhost:8085/swagger-ui.html"
check_http "History Service Swagger"    "http://localhost:8086/swagger-ui.html"

echo ""
echo "── Quick End-to-End: Login ──────────────────────"
LOGIN=$(curl -s --max-time 5 -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"Admin@123"}' 2>/dev/null)

if echo "$LOGIN" | grep -q '"token"'; then
  ok "Login successful — JWT issued"
  TOKEN=$(echo "$LOGIN" | grep -o '"token":"[^"]*"' | cut -d'"' -f4)
  info "Token: ${TOKEN:0:40}..."

  # Quick history check
  HIST=$(curl -s --max-time 5 \
    -H "Authorization: Bearer $TOKEN" \
    "http://localhost:8080/history/runs?page=0&size=1" 2>/dev/null)
  if echo "$HIST" | grep -q '"success":true'; then
    ok "History endpoint reachable"
  else
    fail "History endpoint returned unexpected response"
  fi
else
  fail "Login failed — auth-service may not be ready"
fi

echo ""
echo "═══════════════════════════════════════════════"
if [[ $FAILED -eq 0 ]]; then
  echo -e "${GREEN}  All checks passed!${NC}"
else
  echo -e "${RED}  $FAILED check(s) FAILED — review output above${NC}"
fi
echo "═══════════════════════════════════════════════"
echo ""

exit $FAILED