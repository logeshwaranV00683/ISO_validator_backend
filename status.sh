#!/bin/bash

# ═══════════════════════════════════════════════════════════════
#  ISO 8583 Validator — Service Status
#  Usage: bash status.sh
# ═══════════════════════════════════════════════════════════════

GREEN='\033[0;32m'; RED='\033[0;31m'
CYAN='\033[0;36m'; BOLD='\033[1m'; NC='\033[0m'

# Port check compatible with Windows Git Bash
port_open() {
  local PORT=$1
  if command -v curl &>/dev/null; then
    curl -s --connect-timeout 1 "http://localhost:$PORT" > /dev/null 2>&1
    local EXIT=$?
    [ $EXIT -eq 7 ] || [ $EXIT -eq 28 ] && return 1 || return 0
  elif command -v nc &>/dev/null; then
    nc -z localhost "$PORT" 2>/dev/null; return $?
  else
    (echo > /dev/tcp/localhost/$PORT) 2>/dev/null; return $?
  fi
}

echo -e "${CYAN}${BOLD}"
echo "  ╔══════════════════════════════════════════════╗"
echo "  ║    ISO 8583 Validator — Service Status       ║"
echo "  ╚══════════════════════════════════════════════╝"
echo -e "${NC}"
printf "  ${BOLD}%-26s %-8s %s${NC}\n" "SERVICE" "PORT" "STATUS"
echo "  ──────────────────────────────────────────────"

print_status() {
  local NAME=$1 PORT=$2
  if port_open "$PORT"; then
    printf "  %-26s %-8s " "$NAME" "$PORT"
    echo -e "${GREEN}● RUNNING${NC}"
  else
    printf "  %-26s %-8s " "$NAME" "$PORT"
    echo -e "${RED}○ DOWN${NC}"
  fi
}

print_status "service-registry (Eureka)" 8761
print_status "config-server"             8888
print_status "auth-service"              8081
print_status "profile-service"           8082
print_status "rules-service"             8083
print_status "validation-engine"         8084
print_status "ai-service"                8085
print_status "history-service"           8086
print_status "api-gateway"               8080

echo ""
echo -e "  ${CYAN}── Infrastructure ──────────────────────────${NC}"
print_status "MySQL"    3306
print_status "Redis"    6379
print_status "RabbitMQ" 5672
echo ""
