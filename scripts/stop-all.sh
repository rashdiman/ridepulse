#!/bin/bash

# Цвета для вывода
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${YELLOW}Остановка всех сервисов RidePulse...${NC}"

# Остановка Docker контейнеров
docker-compose down

echo -e "${GREEN}Все сервисы остановлены.${NC}"
echo ""
echo "Для запуска сервисов выполните:"
echo "  ./scripts/start-all.sh"
