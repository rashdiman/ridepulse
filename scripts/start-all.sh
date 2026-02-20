#!/bin/bash

# Цвета для вывода
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${GREEN}======================================${NC}"
echo -e "${GREEN}  RidePulse - Запуск всех сервисов  ${NC}"
echo -e "${GREEN}======================================${NC}"
echo ""

# Проверка наличия Docker
if ! command -v docker &> /dev/null; then
    echo -e "${RED}Docker не установлен. Пожалуйста, установите Docker.${NC}"
    exit 1
fi

# Проверка наличия Docker Compose
if ! command -v docker-compose &> /dev/null && ! docker compose version &> /dev/null; then
    echo -e "${RED}Docker Compose не установлен. Пожалуйста, установите Docker Compose.${NC}"
    exit 1
fi

# Проверка наличия .env файла
if [ ! -f .env ]; then
    echo -e "${YELLOW}.env файл не найден. Создаю из .env.example...${NC}"
    cp .env.example .env
    echo -e "${GREEN}.env файл создан. Пожалуйста, отредактируйте его при необходимости.${NC}"
fi

# Сборка shared-types
echo -e "${YELLOW}Сборка shared-types...${NC}"
cd libs/shared-types
npm install
npm run build
cd ../..

# Запуск Docker контейнеров
echo -e "${YELLOW}Запуск Docker контейнеров...${NC}"
docker-compose up -d

# Ожидание запуска сервисов
echo -e "${YELLOW}Ожидание запуска сервисов...${NC}"
sleep 10

# Проверка статуса сервисов
echo ""
echo -e "${GREEN}Статус сервисов:${NC}"
docker-compose ps

echo ""
echo -e "${GREEN}======================================${NC}"
echo -e "${GREEN}  Сервисы запущены!                  ${NC}"
echo -e "${GREEN}======================================${NC}"
echo ""
echo "API Gateway:      http://localhost:3000"
echo "WebSocket:        ws://localhost:8080/ws"
echo "Metrics Processor: http://localhost:8081"
echo "Alert Engine:     http://localhost:8082"
echo ""
echo "Web Dashboard:    http://localhost:3000 (после запуска)"
echo ""
echo "Для остановки сервисов выполните:"
echo "  ./scripts/stop-all.sh"
echo ""
echo "Для просмотра логов:"
echo "  docker-compose logs -f"
echo ""
