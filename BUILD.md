# Build Guide: RidePulse

## Prerequisites

### General
- Node.js 18+
- npm or yarn
- Docker + Docker Compose (optional)

### Android
- JDK 17+
- Android Studio Hedgehog+
- Android SDK 34
- Gradle 8.2+

## Build Order

### 1. Build shared-types

```bash
cd ridepulse/libs/shared-types
npm install
npm run build
```

### 2. Build backend services

```bash
cd ridepulse/services/api-gateway && npm install && npm run build
cd ridepulse/services/ingest-ws && npm install && npm run build
cd ridepulse/services/metrics-processor && npm install && npm run build
cd ridepulse/services/alert-engine && npm install && npm run build
cd ridepulse/services/gps-processor && npm install && npm run build
cd ridepulse/services/replay-service && npm install && npm run build
```

### 3. Build web dashboard

```bash
cd ridepulse/apps/web-dashboard
npm install
npm run build
```

### 4. Build Android app (unified Rider + Coach)

```bash
cd ridepulse/apps/mobile-rider
./gradlew assembleDebug
# Windows
gradlew.bat assembleDebug
```

APK output:

`apps/mobile-rider/app/build/outputs/apk/debug/app-debug.apk`

## Run in Dev

### Backend + web

```bash
cd ridepulse
docker-compose up -d

cd apps/web-dashboard
npm install
npm run dev
```

### Android

```bash
cd apps/mobile-rider
./gradlew installDebug
```

Inside the app, switch modes:
- `Rider`
- `Coach`

## Validation

```bash
curl http://localhost:3000/health
curl http://localhost:8080/health
curl http://localhost:8081/health
curl http://localhost:8082/health
curl http://localhost:8083/health
curl http://localhost:8084/health
```

```bash
ls apps/mobile-rider/app/build/outputs/apk/debug/
```

## Troubleshooting

### Cannot find module `@ridepulse/shared-types`

```bash
cd libs/shared-types
npm install
npm run build
```

### `gradlew: command not found`

Install JDK 17+ and Android Studio.

### Docker services do not start

```bash
docker-compose logs [service-name]
docker-compose down
docker-compose build
docker-compose up -d
```

