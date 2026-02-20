#!/bin/bash

# Цвета для вывода
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${GREEN}======================================${NC}"
echo -e "${GREEN}  RidePulse - Статус сервисов       ${NC}"
echo -e "${GREEN}======================================${NC}"
echo ""

# Проверка Docker контейнеров
echo -e "${YELLOW}Docker контейнеры:${NC}"
docker-compose ps

echo ""
echo -e "${YELLOW}Health check:${NC}"

# Функция для проверки сервиса
check_service() {
    local name=$1
    local url=$2
    
    if curl -s -f "$url" > /dev/null 2>&1; then
        echo -e "${GREEN}✓${NC} $name доступен"
    else
        echo -e "${RED}✗${NC} $name недоступен"
    fi
}

# Проверка сервисов
check_service "API Gateway" "http://localhost:3000/health"
check_service "Ingest WebSocket" "http://localhost:8080/health"
check_service "Metrics Processor" "http://localhost:8081/health"
check_service "Alert Engine" "http://localhost:8082/health"

echo ""
echo -e "${YELLOW}Порты:${NC}"
echo "PostgreSQL: 5432"
echo "Redis:      6379"
echo "API:        3000"
echo "WebSocket:  8080"
echo "Metrics:    8081"
echo "Alerts:     8082"
