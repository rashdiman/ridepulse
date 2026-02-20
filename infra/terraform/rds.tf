# Subnet Group for RDS
resource "aws_db_subnet_group" "main" {
  name       = "${var.project_name}-${var.environment}-db-subnet-group"
  subnet_ids = aws_subnet.private[*].id
  
  tags = merge(
    var.tags,
    {
      Name = "${var.project_name}-${var.environment}-db-subnet-group"
    }
  )
}

# Security Group for RDS
resource "aws_security_group" "rds" {
  name        = "${var.project_name}-${var.environment}-rds-sg"
  description = "Security group for RDS"
  vpc_id      = aws_vpc.main.id
  
  ingress {
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [aws_eks_cluster.main.vpc_config[0].cluster_security_group_id]
  }
  
  tags = merge(
    var.tags,
    {
      Name = "${var.project_name}-${var.environment}-rds-sg"
    }
  )
}

# RDS Instance
resource "aws_db_instance" "main" {
  identifier     = "${var.project_name}-${var.environment}-rds"
  engine         = "postgres"
  engine_version = "14.10"
  instance_class = var.environment == "prod" ? "db.t3.large" : "db.t3.medium"
  
  allocated_storage     = var.environment == "prod" ? 100 : 20
  max_allocated_storage = var.environment == "prod" ? 500 : 100
  storage_type          = "gp2"
  
  db_name  = "ridepulse"
  username = "postgres"
  
  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = [aws_security_group.rds.id]
  
  multi_az               = var.environment == "prod"
  storage_encrypted      = true
  
  backup_retention_period = var.environment == "prod" ? 30 : 7
  backup_window          = "03:00-04:00"
  maintenance_window     = "Mon:04:00-Mon:05:00"
  
  performance_insights_enabled = var.environment == "prod"
  
  skip_final_snapshot = var.environment != "prod"
  
  tags = merge(
    var.tags,
    {
      Name = "${var.project_name}-${var.environment}-rds"
    }
  )
}

# RDS Parameter Group
resource "aws_db_parameter_group" "main" {
  name   = "${var.project_name}-${var.environment}-pg-param-group"
  family = "postgres14"
  
  parameter {
    name  = "max_connections"
    value = var.environment == "prod" ? "500" : "200"
  }
  
  parameter {
    name  = "shared_buffers"
    value = var.environment == "prod" ? "{DBInstanceClassMemory/4}" : "{DBInstanceClassMemory/8}"
  }
  
  parameter {
    name  = "effective_cache_size"
    value = var.environment == "prod" ? "{DBInstanceClassMemory*3/4}" : "{DBInstanceClassMemory*1/2}"
  }
  
  tags = var.tags
}
