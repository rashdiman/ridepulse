# Инструкции по сборке RidePulse

## Требования

### Общие
- **Node.js** 18+ — https://nodejs.org/
- **npm** или **yarn**
- **Docker** (опционально) — https://www.docker.com/
- **Docker Compose** (опционально)

### Для Android приложений
- **JDK** 17+ — https://www.oracle.com/java/technologies/downloads/
- **Android Studio** Hedgehog (2023.1.1)+ — https://developer.android.com/studio
- **Android SDK** 34
- **Gradle** 8.2+

### Для Kubernetes/Terraform
- **kubectl** — https://kubernetes.io/docs/tasks/tools/
- **terraform** — https://www.terraform.io/downloads.html
- **AWS CLI** (для Terraform) — https://aws.amazon.com/cli/

## Порядок сборки

### 1. Сборка shared-types (обязательно)

```bash
cd ridepulse/libs/shared-types
npm install
npm run build
```

Проверка:
```bash
ls dist/
# Должны быть файлы: index.js, index.d.ts
```

### 2. Сборка backend сервисов

#### API Gateway
```bash
cd ridepulse/services/api-gateway
npm install
npm run build
```

#### Ingest WebSocket
```bash
cd ridepulse/services/ingest-ws
npm install
npm run build
```

#### Metrics Processor
```bash
cd ridepulse/services/metrics-processor
npm install
npm run build
```

#### Alert Engine
```bash
cd ridepulse/services/alert-engine
npm install
npm run build
```

#### GPS Processor
```bash
cd ridepulse/services/gps-processor
npm install
npm run build
```

#### Replay Service
```bash
cd ridepulse/services/replay-service
npm install
npm run build
```

### 3. Сборка Web Dashboard

```bash
cd ridepulse/apps/web-dashboard
npm install
npm run build
```

### 4. Сборка Android приложений

#### Mobile Rider
```bash
cd ridepulse/apps/mobile-rider
./gradlew assembleDebug
# Или на Windows:
gradlew.bat assembleDebug
```

APK будет в: `app/build/outputs/apk/debug/app-debug.apk`

#### Mobile Coach
```bash
cd ridepulse/apps/mobile-coach
./gradlew assembleDebug
# Или на Windows:
gradlew.bat assembleDebug
```

APK будет в: `app/build/outputs/apk/debug/app-debug.apk`

## Запуск через Docker Compose (рекомендуется)

### 1. Настройка переменных окружения

```bash
cd ridepulse
cp .env.example .env
# Отредактируйте .env при необходимости
```

### 2. Запуск инфраструктуры

```bash
docker-compose up -d
```

### 3. Проверка статуса

```bash
docker-compose ps
```

### 4. Просмотр логов

```bash
# Все сервисы
docker-compose logs -f

# Конкретный сервис
docker-compose logs -f api-gateway
docker-compose logs -f ingest-ws
```

### 5. Остановка

```bash
docker-compose down
```

## Запуск в dev режиме

### 1. Запуск инфраструктуры (PostgreSQL + Redis)

```bash
# PostgreSQL
docker run -d --name ridepulse-postgres \
  -e POSTGRES_DB=ridepulse \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=postgres \
  -p 5432:5432 \
  postgres:14

# Redis
docker run -d --name ridepulse-redis \
  -p 6379:6379 \
  redis:7-alpine
```

### 2. Сборка shared-types

```bash
cd libs/shared-types
npm install
npm run build
```

### 3. Запуск backend сервисов (в отдельных терминалах)

```bash
# Terminal 1 - API Gateway
cd services/api-gateway
npm install
npm run dev

# Terminal 2 - Ingest WebSocket
cd services/ingest-ws
npm install
npm run dev

# Terminal 3 - Metrics Processor
cd services/metrics-processor
npm install
npm run dev

# Terminal 4 - Alert Engine
cd services/alert-engine
npm install
npm run dev

# Terminal 5 - GPS Processor
cd services/gps-processor
npm install
npm run dev

# Terminal 6 - Replay Service
cd services/replay-service
npm install
npm run dev
```

### 4. Запуск Web Dashboard

```bash
cd apps/web-dashboard
npm install
npm run dev
```

Откройте: http://localhost:3000

### 5. Установка Android приложений

```bash
# Mobile Rider
cd apps/mobile-rider
./gradlew installDebug

# Mobile Coach
cd apps/mobile-coach
./gradlew installDebug
```

## Развертывание в Kubernetes

### 1. Создание namespace

```bash
kubectl apply -f infra/k8s/namespace.yaml
```

### 2. Создание секретов

```bash
kubectl apply -f infra/k8s/secret.yaml
```

### 3. Создание ConfigMap

```bash
kubectl apply -f infra/k8s/configmap.yaml
```

### 4. Развертывание инфраструктуры

```bash
kubectl apply -f infra/k8s/postgres.yaml
kubectl apply -f infra/k8s/redis.yaml
```

### 5. Сборка Docker образов

```bash
# Сборка всех образов
./scripts/build-images.sh

# Или по отдельности
cd services/api-gateway
docker build -f ../../infra/docker/Dockerfile.api-gateway -t ridepulse/api-gateway:latest .
```

### 6. Развертывание сервисов

```bash
kubectl apply -f infra/k8s/
```

### 7. Проверка статуса

```bash
kubectl get pods -n ridepulse
kubectl get svc -n ridepulse
```

## Развертывание в AWS через Terraform

### 1. Настройка AWS CLI

```bash
aws configure
```

### 2. Инициализация Terraform

```bash
cd infra/terraform
terraform init
```

### 3. Создание файла переменных

```bash
cat > terraform.tfvars << EOF
aws_region   = "us-east-1"
environment  = "dev"
cluster_name = "ridepulse-eks-dev"
domain_name  = "ridepulse.dev"
EOF
```

### 4. Планирование

```bash
terraform plan
```

### 5. Применение

```bash
terraform apply
```

### 6. Настройка kubectl

```bash
aws eks update-kubeconfig --region us-east-1 --name ridepulse-eks-dev
```

### 7. Развертывание приложений

```bash
kubectl apply -f ../../k8s/
```

## Проверка сборки

### Backend сервисы

```bash
# API Gateway
curl http://localhost:3000/health

# Ingest WebSocket
curl http://localhost:8080/health

# Metrics Processor
curl http://localhost:8081/health

# Alert Engine
curl http://localhost:8082/health

# GPS Processor
curl http://localhost:8083/health

# Replay Service
curl http://localhost:8084/health
```

### Web Dashboard

Откройте: http://localhost:3000

### Android приложения

Установите APK файлы на устройство:
- `apps/mobile-rider/app/build/outputs/apk/debug/app-debug.apk`
- `apps/mobile-coach/app/build/outputs/apk/debug/app-debug.apk`

## Troubleshooting

### Ошибка: "Cannot find module '@ridepulse/shared-types'"

Решение:
```bash
cd libs/shared-types
npm install
npm run build
```

### Ошибка: "npm command not found"

Решение: Установите Node.js с https://nodejs.org/

### Ошибка: "gradlew: command not found"

Решение: Установите JDK и Android Studio

### Docker контейнер не запускается

Решение:
```bash
# Просмотр логов
docker-compose logs [service-name]

# Пересборка
docker-compose down
docker-compose build
docker-compose up -d
```

### Kubernetes поды не запускаются

Решение:
```bash
# Просмотр подов
kubectl get pods -n ridepulse

# Просмотр событий
kubectl get events -n ridepulse

# Просмотр логов
kubectl logs <pod-name> -n ridepulse

# Описание пода
kubectl describe pod <pod-name> -n ridepulse
```

## Структура после сборки

```
ridepulse/
├── libs/shared-types/dist/     # Собранная библиотека
├── services/*/dist/             # Собранные backend сервисы
├── apps/web-dashboard/.next/    # Собранный Next.js
├── apps/mobile-rider/app/build/outputs/apk/debug/
├── apps/mobile-coach/app/build/outputs/apk/debug/
```

## Следующие шаги

1. Установите Node.js: https://nodejs.org/
2. Склонируйте репозиторий
3. Выполните команды из раздела "Запуск через Docker Compose"
4. Установите Android приложения
5. Начните использование!

## Дополнительные ресурсы

- [Node.js Documentation](https://nodejs.org/docs/)
- [Docker Documentation](https://docs.docker.com/)
- [Kubernetes Documentation](https://kubernetes.io/docs/)
- [Android Studio Documentation](https://developer.android.com/studio)
