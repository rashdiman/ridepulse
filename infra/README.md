# RidePulse Инфраструктура

Документация по развёртыванию и управлению инфраструктурой RidePulse.

## Содержание

- [Docker](#docker)
- [Kubernetes](#kubernetes)
- [Terraform](#terraform)

## Docker

### Структура Dockerfile

```
infra/docker/
├── Dockerfile.api-gateway        # API Gateway
├── Dockerfile.ingest-ws          # Ingest WebSocket
├── Dockerfile.metrics-processor  # Metrics Processor
├── Dockerfile.alert-engine        # Alert Engine
├── Dockerfile.gps-processor      # GPS Processor
├── Dockerfile.replay-service     # Replay Service
└── Dockerfile.web-dashboard      # Web Dashboard
```

### Использование Docker Compose

Запуск всех сервисов:

```bash
docker-compose up -d
```

Остановка всех сервисов:

```bash
docker-compose down
```

Просмотр логов:

```bash
docker-compose logs -f [service-name]
```

### Сборка Docker образов

```bash
# API Gateway
cd services/api-gateway
docker build -f ../../infra/docker/Dockerfile.api-gateway -t ridepulse/api-gateway:latest .

# Ingest WebSocket
cd services/ingest-ws
docker build -f ../../infra/docker/Dockerfile.ingest-ws -t ridepulse/ingest-ws:latest .

# Metrics Processor
cd services/metrics-processor
docker build -f ../../infra/docker/Dockerfile.metrics-processor -t ridepulse/metrics-processor:latest .

# Alert Engine
cd services/alert-engine
docker build -f ../../infra/docker/Dockerfile.alert-engine -t ridepulse/alert-engine:latest .

# GPS Processor
cd services/gps-processor
docker build -f ../../infra/docker/Dockerfile.gps-processor -t ridepulse/gps-processor:latest .

# Replay Service
cd services/replay-service
docker build -f ../../infra/docker/Dockerfile.replay-service -t ridepulse/replay-service:latest .

# Web Dashboard
cd apps/web-dashboard
docker build -f ../../infra/docker/Dockerfile.web-dashboard -t ridepulse/web-dashboard:latest .
```

## Kubernetes

### Структура манифестов

```
infra/k8s/
├── namespace.yaml              # Namespace
├── secret.yaml                 # Secrets
├── configmap.yaml              # ConfigMaps
├── postgres.yaml               # PostgreSQL
├── redis.yaml                  # Redis
├── api-gateway.yaml            # API Gateway
├── ingest-ws.yaml              # Ingest WebSocket
├── metrics-processor.yaml      # Metrics Processor
├── alert-engine.yaml           # Alert Engine
├── gps-processor.yaml          # GPS Processor
├── replay-service.yaml         # Replay Service
└── web-dashboard.yaml          # Web Dashboard
```

### Развертывание в Kubernetes

1. **Создание namespace**

```bash
kubectl apply -f infra/k8s/namespace.yaml
```

2. **Создание секретов**

```bash
kubectl apply -f infra/k8s/secret.yaml
```

3. **Создание ConfigMaps**

```bash
kubectl apply -f infra/k8s/configmap.yaml
```

4. **Развертывание инфраструктуры**

```bash
kubectl apply -f infra/k8s/postgres.yaml
kubectl apply -f infra/k8s/redis.yaml
```

5. **Развертывание сервисов**

```bash
kubectl apply -f infra/k8s/api-gateway.yaml
kubectl apply -f infra/k8s/ingest-ws.yaml
kubectl apply -f infra/k8s/metrics-processor.yaml
kubectl apply -f infra/k8s/alert-engine.yaml
kubectl apply -f infra/k8s/gps-processor.yaml
kubectl apply -f infra/k8s/replay-service.yaml
kubectl apply -f infra/k8s/web-dashboard.yaml
```

6. **Развертывание всех сервисов одной командой**

```bash
kubectl apply -f infra/k8s/
```

### Управление Kubernetes

Просмотр подов:

```bash
kubectl get pods -n ridepulse
```

Просмотр сервисов:

```bash
kubectl get svc -n ridepulse
```

Просмотр логов:

```bash
kubectl logs -f <pod-name> -n ridepulse
```

Масштабирование:

```bash
kubectl scale deployment api-gateway --replicas=3 -n ridepulse
```

Получение внешнего IP:

```bash
kubectl get svc -n ridepulse
```

### Horizontal Pod Autoscaler

Некоторые сервисы имеют HPA для автоматического масштабирования:

```bash
# Просмотр HPA
kubectl get hpa -n ridepulse

# Ручное масштабирование
kubectl autoscale deployment api-gateway --min=2 --max=10 --cpu-percent=70 -n ridepulse
```

## Terraform

### Структура файлов

```
infra/terraform/
├── main.tf                    # Основная конфигурация
├── variables.tf               # Переменные
├── outputs.tf                 # Outputs
├── vpc.tf                     # VPC и сеть
├── eks.tf                     # EKS кластер
├── rds.tf                     # PostgreSQL RDS
├── elasticache.tf             # Redis ElastiCache
└── loadbalancer.tf            # Load Balancer и Route53
```

### Развертывание в AWS

1. **Настройка переменных**

Создайте файл `terraform.tfvars`:

```hcl
aws_region   = "us-east-1"
environment  = "prod"
domain_name  = "ridepulse.com"
```

2. **Инициализация Terraform**

```bash
cd infra/terraform
terraform init
```

3. **Планирование изменений**

```bash
terraform plan
```

4. **Применение изменений**

```bash
terraform apply
```

5. **Уничтожение ресурсов**

```bash
terraform destroy
```

### Получение доступа к EKS

После создания кластера:

```bash
aws eks update-kubeconfig --region us-east-1 --name ridepulse-eks-prod
```

### Переменные Terraform

| Переменная | Описание | По умолчанию |
|-----------|----------|--------------|
| `aws_region` | AWS регион | `us-east-1` |
| `environment` | Окружение | `dev` |
| `cluster_name` | Название кластера | `ridepulse-eks` |
| `node_group_instance_types` | Типы инстансов | `["t3.medium"]` |
| `node_group_desired_size` | Желаемое количество нод | `3` |
| `domain_name` | Доменное имя | `ridepulse.com` |
| `enable_cloudwatch` | CloudWatch логирование | `true` |

### Outputs

После применения Terraform вы получите:

- VPC ID и CIDR
- ID подсетей
- EKS кластер endpoint
- RDS endpoint
- ElastiCache endpoint
- Load Balancer DNS

## Мониторинг и логирование

### Prometheus и Grafana (в планах)

```bash
# Установка Prometheus Operator
kubectl apply -f infra/k8s/monitoring/prometheus-operator.yaml

# Установка Grafana
kubectl apply -f infra/k8s/monitoring/grafana.yaml
```

### CloudWatch

Для AWS окружения включено CloudWatch логирование:

```bash
# Просмотр логов
aws logs tail /aws/eks/ridepulse-prod/cluster --follow

# Просмотр логов конкретного пода
kubectl logs <pod-name> -n ridepulse
```

## Резервное копирование

### PostgreSQL

```bash
# Создание бэкапа
kubectl exec -it postgres-0 -n ridepulse -- pg_dump -U postgres ridepulse > backup.sql

# Восстановление из бэкапа
cat backup.sql | kubectl exec -i postgres-0 -n ridepulse -- psql -U postgres ridepulse
```

### Redis

```bash
# Сохранение snapshot
kubectl exec -it redis-0 -n ridepulse -- redis-cli BGSAVE

# Копирование файла
kubectl cp ridepulse/redis-0:/data/dump.rdb ./dump.rdb
```

## Troubleshooting

### Проверка состояния подов

```bash
kubectl get pods -n ridepulse
kubectl describe pod <pod-name> -n ridepulse
```

### Проверка событий

```bash
kubectl get events -n ridepulse --sort-by='.lastTimestamp'
```

### Проверка логов

```bash
# Все поды
kubectl logs -l app=api-gateway -n ridepulse --all-containers=true

# Конкретный под
kubectl logs <pod-name> -n ridepulse --tail=100 -f
```

### Проверка подключения к БД

```bash
kubectl exec -it postgres-0 -n ridepulse -- psql -U postgres -d ridepulse
```

### Проверка Redis

```bash
kubectl exec -it redis-0 -n ridepulse -- redis-cli
> PING
> INFO
```

## Безопасность

### Secrets

Все секреты хранятся в Kubernetes Secrets:

```bash
kubectl get secrets -n ridepulse
kubectl describe secret ridepulse-secrets -n ridepulse
```

### RBAC

Для production окружения рекомендуется настроить RBAC:

```bash
kubectl apply -f infra/k8s/rbac/
```

### Network Policies

Ограничение сетевого трафика между подами:

```bash
kubectl apply -f infra/k8s/network-policies/
```

## CI/CD

### GitHub Actions (в планах)

`.github/workflows/deploy.yml`:

```yaml
name: Deploy to Kubernetes

on:
  push:
    branches: [main]

jobs:
  deploy:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v2
      - name: Build and push Docker images
        run: |
          # Build and push images
      - name: Deploy to Kubernetes
        run: |
          kubectl apply -f infra/k8s/
```

## Дополнительные ресурсы

- [Kubernetes Documentation](https://kubernetes.io/docs/)
- [Terraform Documentation](https://www.terraform.io/docs)
- [AWS EKS Documentation](https://docs.aws.amazon.com/eks/)
