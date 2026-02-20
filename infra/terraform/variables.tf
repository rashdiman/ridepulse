variable "aws_region" {
  description = "AWS регион для развёртывания"
  type        = string
  default     = "us-east-1"
}

variable "project_name" {
  description = "Название проекта"
  type        = string
  default     = "ridepulse"
}

variable "environment" {
  description = "Окружение (dev, staging, prod)"
  type        = string
  default     = "dev"
  validation {
    condition     = contains(["dev", "staging", "prod"], var.environment)
    error_message = "Environment must be dev, staging, or prod."
  }
}

variable "vpc_cidr" {
  description = "CIDR блок для VPC"
  type        = string
  default     = "10.0.0.0/16"
}

variable "availability_zones" {
  description = "Список availability zones"
  type        = list(string)
  default     = ["us-east-1a", "us-east-1b"]
}

variable "cluster_name" {
  description = "Название EKS кластера"
  type        = string
  default     = "ridepulse-eks"
}

variable "cluster_version" {
  description = "Версия Kubernetes"
  type        = string
  default     = "1.28"
}

variable "node_group_instance_types" {
  description = "Типы инстансов для node group"
  type        = list(string)
  default     = ["t3.medium"]
}

variable "node_group_desired_size" {
  description = "Желаемое количество нод"
  type        = number
  default     = 3
}

variable "node_group_max_size" {
  description = "Максимальное количество нод"
  type        = number
  default     = 5
}

variable "node_group_min_size" {
  description = "Минимальное количество нод"
  type        = number
  default     = 1
}

variable "domain_name" {
  description = "Доменное имя для приложения"
  type        = string
  default     = "ridepulse.com"
}

variable "enable_cloudwatch" {
  description = "Включить CloudWatch логирование"
  type        = bool
  default     = true
}

variable "tags" {
  description = "Теги для всех ресурсов"
  type        = map(string)
  default = {
    Project     = "RidePulse"
    ManagedBy   = "Terraform"
  }
}
