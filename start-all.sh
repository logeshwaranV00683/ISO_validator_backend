#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
#  ISO 8583 Validator — Start All Spring Boot Services
#  Run from the repo root after: docker-compose up -d
# ─────────────────────────────────────────────────────────────────────────────

set -e

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOG_DIR="$REPO_ROOT/logs"
mkdir -p "$LOG_DIR"

start_service() {
  local name="$1"
  local dir="$2"
  local wait_secs="${3:-10}"

  echo "▶  Starting $name..."
  cd "$REPO_ROOT/$dir"
  ./mvnw spring-boot:run > "$LOG_DIR/$name.log" 2>&1 &
  echo "   PID $! — logs: logs/$name.log"
  echo "   Waiting ${wait_secs}s..."
  sleep "$wait_secs"
}

echo ""
echo "════════════════════════════════════════"
echo "  Building common-lib..."
echo "════════════════════════════════════════"
cd "$REPO_ROOT/common-lib"
./mvnw clean install -DskipTests -q
echo "  ✓ common-lib installed"

echo ""
echo "════════════════════════════════════════"
echo "  Starting services in order..."
echo "════════════════════════════════════════"

start_service "service-registry"  "service-registry"  20
start_service "config-server"     "config-server"     15
start_service "auth-service"      "auth-service"      15
start_service "profile-service"   "profile-service"   12
start_service "rules-service"     "rules-service"     12
start_service "ai-service"        "ai-service"        12
start_service "validation-engine" "validation-engine" 12
start_service "history-service"   "history-service"   12
start_service "api-gateway"       "api-gateway"       15

echo ""
echo "════════════════════════════════════════"
echo "  All services started."
echo "  Running health check..."
echo "════════════════════════════════════════"
echo ""
"$REPO_ROOT/health-check.sh"