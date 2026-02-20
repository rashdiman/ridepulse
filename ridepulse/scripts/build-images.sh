#!/bin/bash

# Цвета для вывода
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${GREEN}======================================${NC}"
echo -e "${GREEN}  RidePulse - Сборка Docker образов  ${NC}"
echo -e "${GREEN}======================================${NC}"
echo ""

# Проверка наличия Docker
if ! command -v docker &> /dev/null; then
    echo -e "${RED}Docker не установлен. Пожалуйста, установите Docker.${NC}"
    exit 1
fi

# Проверка наличия Docker Compose
if ! command -v docker-compose &> /dev/null; then
    echo -e "${RED}Docker Compose не установлен. Пожалуйста, установите Docker Compose.${NC}"
    exit 1
fi

# Сборка shared-types
echo -e "${YELLOW}1. Сборка shared-types...${NC}"
cd libs/shared-types
if [ -f "package.json" ]; then
    npm install
    npm run build
    echo -e "${GREEN}✓ shared-types собран${NC}"
else
    echo -e "${YELLOW}⚠ package.json не найден, пропускаем${NC}"
fi
cd ../..

# Сборка Docker образов
echo ""
echo -e "${YELLOW}2. Сборка Docker образов...${NC}"

images=(
    "api-gateway"
    "ingest-ws"
    "metrics-processor"
    "alert-engine"
    "gps-processor"
    "replay-service"
    "web-dashboard"
)

for image in "${images[@]}"; do
    echo -e "${YELLOW}Сборка: $image${NC}"
    
    case $image in
        api-gateway)
            docker build -f infra/docker/Dockerfile.api-gateway -t ridepulse/api-gateway:latest ./services/api-gateway
            ;;
        ingest-ws)
            docker build -f infra/docker/Dockerfile.ingest-ws -t ridepulse/ingest-ws:latest ./services/ingest-ws
            ;;
        metrics-processor)
            docker build -f infra/docker/Dockerfile.metrics-processor -t ridepulse/metrics-processor:latest ./services/metrics-processor
            ;;
        alert-engine)
            docker build -f infra/docker/Dockerfile.alert-engine -t ridepulse/alert-engine:latest ./services/alert-engine
            ;;
        gps-processor)
            docker build -f infra/docker/Dockerfile.gps-processor -t ridepulse/gps-processor:latest ./services/gps-processor
            ;;
        replay-service)
            docker build -f infra/docker/Dockerfile.replay-service -t ridepulse/replay-service:latest ./services/replay-service
            ;;
        web-dashboard)
            docker build -f infra/docker/Dockerfile.web-dashboard -t ridepulse/web-dashboard:latest ./apps/web-dashboard
            ;;
    esac
    
    if [ $? -eq 0 ]; then
        echo -e "${GREEN}✓ $image собран${NC}"
    else
        echo -e "${RED}✗ Ошибка сборки $image${NC}"
    fi
done

echo ""
echo -e "${GREEN}======================================${NC}"
echo -e "${GREEN}  Сборка завершена!                   ${NC}"
echo -e "${GREEN}======================================${NC}"
echo ""
echo "Собранные образы:"
docker images | grep ridepulse
echo ""
echo "Для запуска сервисов выполните:"
echo "  docker-compose up -d"
