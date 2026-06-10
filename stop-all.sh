#!/bin/bash

# ═══════════════════════════════════════════════════════════════
#  ISO 8583 Validator — Stop All Services
#  Usage: bash stop-all.sh
# ═══════════════════════════════════════════════════════════════

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'
CYAN='\033[0;36m'; BOLD='\033[1m'; NC='\033[0m'

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
PID_DIR="$ROOT_DIR/.service-pids"

echo -e "${CYAN}${BOLD}\n  Stopping ISO 8583 Validator services...\n${NC}"

# Reverse order: gateway first, registry last
SERVICES=(
  "api-gateway"
  "history-service"
  "ai-service"
  "validation-engine"
  "rules-service"
  "profile-service"
  "auth-service"
  "config-server"
  "service-registry"
)

for SERVICE in "${SERVICES[@]}"; do
  PID_FILE="$PID_DIR/$SERVICE.pid"
  if [ -f "$PID_FILE" ]; then
    PID=$(cat "$PID_FILE")
    # On Windows Git Bash, kill the process tree
    if [[ "$OSTYPE" == "msys" || "$OSTYPE" == "cygwin" ]]; then
      taskkill //F //T //PID "$PID" > /dev/null 2>&1
    else
      # Linux/Mac: kill process group
      PGID=$(ps -o pgid= -p "$PID" 2>/dev/null | tr -d ' ')
      if [ -n "$PGID" ] && [ "$PGID" != "0" ]; then
        kill -- -"$PGID" 2>/dev/null
      else
        kill "$PID" 2>/dev/null
      fi
    fi
    echo -e "  ${GREEN}✓ Stopped $SERVICE${NC}"
    rm -f "$PID_FILE"
  else
    echo -e "  ${YELLOW}–  $SERVICE not started via this script${NC}"
  fi
done

echo -e "\n  ${GREEN}${BOLD}All stopped.${NC}\n"
