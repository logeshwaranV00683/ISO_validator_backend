#!/bin/bash

# ═══════════════════════════════════════════════════════════════
#  ISO 8583 Validator — Start All Services
#  Compatible: Windows Git Bash + Linux/Mac
#  Run from  : ISO_validator_backend/ root folder
#  Usage     : bash start-all.sh
# ═══════════════════════════════════════════════════════════════

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'
CYAN='\033[0;36m'; BOLD='\033[1m'; NC='\033[0m'

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
LOG_DIR="$ROOT_DIR/.service-logs"
PID_DIR="$ROOT_DIR/.service-pids"
mkdir -p "$LOG_DIR" "$PID_DIR"

echo -e "${CYAN}${BOLD}"
echo "  ╔═════════════════════════════════════════════════╗"
echo "  ║   ISO 8583 Validator — Starting All Services   ║"
echo "  ╚═════════════════════════════════════════════════╝"
echo -e "${NC}"

# ── Port check: works on Git Bash (Windows) + Linux ───────────
port_open() {
  local PORT=$1
  # Try curl first (available in Git Bash), fallback to nc
  if command -v curl &>/dev/null; then
    curl -s --connect-timeout 1 "http://localhost:$PORT" > /dev/null 2>&1
    local EXIT=$?
    # curl exit 7 = connection refused, 28 = timeout — both mean DOWN
    # exit 0, 22, 52, 56 = connected (server responded, even with error)
    if [ $EXIT -eq 7 ] || [ $EXIT -eq 28 ]; then
      return 1
    else
      return 0
    fi
  elif command -v nc &>/dev/null; then
    nc -z localhost "$PORT" 2>/dev/null
    return $?
  else
    # Last resort: /dev/tcp (bash built-in, works in Git Bash)
    (echo > /dev/tcp/localhost/$PORT) 2>/dev/null
    return $?
  fi
}

# ── Wait until port is UP ──────────────────────────────────────
wait_for_port() {
  local NAME=$1 PORT=$2 TIMEOUT=${3:-90}
  echo -ne "  ${YELLOW}⏳ Waiting for $NAME (port $PORT)...${NC}"
  for i in $(seq 1 $TIMEOUT); do
    if port_open "$PORT"; then
      echo -e " ${GREEN}✓ UP${NC}"; return 0
    fi
    sleep 1
  done
  echo -e " ${RED}✗ TIMEOUT — check ${LOG_DIR}/${NAME}.log${NC}"
  return 1
}

# ── Launch one service with mvn spring-boot:run ────────────────
start_service() {
  local NAME=$1 DIR=$2
  local FULL_DIR="$ROOT_DIR/$DIR"

  if [ ! -d "$FULL_DIR" ]; then
    echo -e "  ${RED}✗ '$DIR' not found — skipping $NAME${NC}"; return 1
  fi

  echo -e "  ${CYAN}▶ Starting $NAME...${NC}"

  # Remove compiled classes — forces recompile on source changes
  # (avoids stale .class files without full mvn clean install)
  rm -rf "$FULL_DIR/target/classes"

  (
    cd "$FULL_DIR"
    mvn spring-boot:run \
      -Dspring-boot.run.jvmArguments="-Xms128m -Xmx512m" \
      > "$LOG_DIR/$NAME.log" 2>&1
  ) &

  echo $! > "$PID_DIR/$NAME.pid"
}

# ════════════════════════════════════════════════════════════════
#  STEP 1 — Infrastructure checks (MySQL / Redis / RabbitMQ)
# ════════════════════════════════════════════════════════════════
echo -e "\n${BOLD}[1/5] Checking infrastructure...${NC}"

INFRA_OK=true

check_infra() {
  local LABEL=$1 PORT=$2
  if port_open "$PORT"; then
    echo -e "  ${GREEN}✓ $LABEL OK (port $PORT)${NC}"
  else
    echo -e "  ${RED}✗ $LABEL NOT running on port $PORT — start it first!${NC}"
    INFRA_OK=false
  fi
}

check_infra "MySQL"    3306
check_infra "Redis"    6379
check_infra "RabbitMQ" 5672

if [ "$INFRA_OK" = false ]; then
  echo -e "\n  ${RED}Fix infrastructure issues above, then re-run.${NC}\n"
  exit 1
fi

# ════════════════════════════════════════════════════════════════
#  STEP 2 — Build common-lib (always fresh install)
# ════════════════════════════════════════════════════════════════
echo -e "\n${BOLD}[2/5] Building common-lib...${NC}"

if [ -d "$ROOT_DIR/common-lib" ]; then
  cd "$ROOT_DIR/common-lib"
  echo -ne "  ${YELLOW}⏳ Running mvn clean install -DskipTests...${NC}"
  mvn clean install -q -DskipTests
  if [ $? -ne 0 ]; then
    echo -e "\n  ${RED}✗ common-lib build failed! See output above.${NC}\n"
    exit 1
  fi
  echo -e " ${GREEN}✓ common-lib installed to ~/.m2${NC}"
  cd "$ROOT_DIR"
else
  echo -e "  ${YELLOW}⚠ common-lib directory not found — skipping${NC}"
fi

# ════════════════════════════════════════════════════════════════
#  STEP 3 — Eureka + Config Server (must be up before others)
# ════════════════════════════════════════════════════════════════
echo -e "\n${BOLD}[3/5] Starting infrastructure services...${NC}"

start_service "service-registry" "service-registry"
wait_for_port  "service-registry" 8761 90 || exit 1

start_service "config-server" "config-server"
wait_for_port  "config-server" 8888 60 || exit 1

# ════════════════════════════════════════════════════════════════
#  STEP 4 — Business services (all parallel)
# ════════════════════════════════════════════════════════════════
echo -e "\n${BOLD}[4/5] Starting business services (parallel)...${NC}"

start_service "auth-service"      "auth-service"
start_service "profile-service"   "profile-service"
start_service "rules-service"     "rules-service"
start_service "validation-engine" "validation-engine"
start_service "ai-service"        "ai-service"
start_service "history-service"   "history-service"

wait_for_port "auth-service"      8081 120
wait_for_port "profile-service"   8082 120
wait_for_port "rules-service"     8083 120
wait_for_port "validation-engine" 8084 120
wait_for_port "ai-service"        8085 120
wait_for_port "history-service"   8086 120

# ════════════════════════════════════════════════════════════════
#  STEP 5 — API Gateway last
# ════════════════════════════════════════════════════════════════
echo -e "\n${BOLD}[5/5] Starting API Gateway...${NC}"

start_service "api-gateway" "api-gateway"
wait_for_port "api-gateway" 8080 90 || exit 1

# ════════════════════════════════════════════════════════════════
#  Done
# ════════════════════════════════════════════════════════════════
echo ""
echo -e "${GREEN}${BOLD}  ╔═══════════════════════════════════════════════════════╗${NC}"
echo -e "${GREEN}${BOLD}  ║              All Services Running ✅                  ║${NC}"
echo -e "${GREEN}${BOLD}  ╠═══════════════════════════╦═══════════════════════════╣${NC}"
echo -e "${GREEN}  ║  Service Registry (Eureka) ║  http://localhost:8761     ║${NC}"
echo -e "${GREEN}  ║  Config Server            ║  http://localhost:8888     ║${NC}"
echo -e "${GREEN}  ║  Auth Service             ║  http://localhost:8081     ║${NC}"
echo -e "${GREEN}  ║  Profile Service          ║  http://localhost:8082     ║${NC}"
echo -e "${GREEN}  ║  Rules Service            ║  http://localhost:8083     ║${NC}"
echo -e "${GREEN}  ║  Validation Engine        ║  http://localhost:8084     ║${NC}"
echo -e "${GREEN}  ║  AI Service               ║  http://localhost:8085     ║${NC}"
echo -e "${GREEN}  ║  History Service          ║  http://localhost:8086     ║${NC}"
echo -e "${GREEN}  ║  API Gateway              ║  http://localhost:8080     ║${NC}"
echo -e "${GREEN}${BOLD}  ╚═══════════════════════════╩═══════════════════════════╝${NC}"
echo ""
echo -e "  ${CYAN}Logs  : $LOG_DIR/${NC}"
echo -e "  ${CYAN}Stop  : bash stop-all.sh${NC}"
echo -e "  ${CYAN}Status: bash status.sh${NC}"
echo ""
