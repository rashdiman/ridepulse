# RidePulse - Сборка Docker образов (PowerShell)

Write-Host "======================================" -ForegroundColor Green
Write-Host "  RidePulse - Сборка Docker образов  " -ForegroundColor Green
Write-Host "======================================" -ForegroundColor Green
Write-Host ""

# Проверка наличия Docker
$dockerExists = Get-Command docker -ErrorAction SilentlyContinue
if (-not $dockerExists) {
    Write-Host "Docker не установлен. Пожалуйста, установите Docker Desktop." -ForegroundColor Red
    exit 1
}

# Проверка наличия Docker Compose
$dockerComposeExists = Get-Command docker-compose -ErrorAction SilentlyContinue
if (-not $dockerComposeExists) {
    Write-Host "Docker Compose не установлен. Пожалуйста, установите Docker Desktop." -ForegroundColor Red
    exit 1
}

# Сборка shared-types
Write-Host "1. Сборка shared-types..." -ForegroundColor Yellow
Set-Location libs/shared-types
if (Test-Path "package.json") {
    npm install
    npm run build
    Write-Host "✓ shared-types собран" -ForegroundColor Green
} else {
    Write-Host "⚠ package.json не найден, пропускаем" -ForegroundColor Yellow
}
Set-Location ../..

# Сборка Docker образов
Write-Host ""
Write-Host "2. Сборка Docker образов..." -ForegroundColor Yellow

$images = @{
    "api-gateway" = @{
        dockerfile = "infra/docker/Dockerfile.api-gateway"
        context = "./services/api-gateway"
        tag = "ridepulse/api-gateway:latest"
    }
    "ingest-ws" = @{
        dockerfile = "infra/docker/Dockerfile.ingest-ws"
        context = "./services/ingest-ws"
        tag = "ridepulse/ingest-ws:latest"
    }
    "metrics-processor" = @{
        dockerfile = "infra/docker/Dockerfile.metrics-processor"
        context = "./services/metrics-processor"
        tag = "ridepulse/metrics-processor:latest"
    }
    "alert-engine" = @{
        dockerfile = "infra/docker/Dockerfile.alert-engine"
        context = "./services/alert-engine"
        tag = "ridepulse/alert-engine:latest"
    }
    "gps-processor" = @{
        dockerfile = "infra/docker/Dockerfile.gps-processor"
        context = "./services/gps-processor"
        tag = "ridepulse/gps-processor:latest"
    }
    "replay-service" = @{
        dockerfile = "infra/docker/Dockerfile.replay-service"
        context = "./services/replay-service"
        tag = "ridepulse/replay-service:latest"
    }
    "web-dashboard" = @{
        dockerfile = "infra/docker/Dockerfile.web-dashboard"
        context = "./apps/web-dashboard"
        tag = "ridepulse/web-dashboard:latest"
    }
}

foreach ($image in $images.Keys) {
    $config = $images[$image]
    Write-Host "Сборка: $image" -ForegroundColor Yellow
    
    $buildArgs = @(
        "build",
        "-f", $config.dockerfile,
        "-t", $config.tag,
        $config.context
    )
    
    $result = & docker @buildArgs 2>&1
    
    if ($LASTEXITCODE -eq 0) {
        Write-Host "✓ $image собран" -ForegroundColor Green
    } else {
        Write-Host "✗ Ошибка сборки $image" -ForegroundColor Red
        Write-Host $result
    }
}

Write-Host ""
Write-Host "======================================" -ForegroundColor Green
Write-Host "  Сборка завершена!                   " -ForegroundColor Green
Write-Host "======================================" -ForegroundColor Green
Write-Host ""

Write-Host "Собранные образы:"
docker images | Select-String "ridepulse"
Write-Host ""

Write-Host "Для запуска сервисов выполните:"
Write-Host "  docker-compose up -d"
