output "vpc_id" {
  description = "ID VPC"
  value       = aws_vpc.main.id
}

output "vpc_cidr" {
  description = "CIDR блок VPC"
  value       = aws_vpc.main.cidr_block
}

output "private_subnet_ids" {
  description = "ID приватных подсетей"
  value       = aws_subnet.private[*].id
}

output "public_subnet_ids" {
  description = "ID публичных подсетей"
  value       = aws_subnet.public[*].id
}

output "eks_cluster_id" {
  description = "ID EKS кластера"
  value       = aws_eks_cluster.main.id
}

output "eks_cluster_endpoint" {
  description = "Endpoint EKS кластера"
  value       = aws_eks_cluster.main.endpoint
}

output "eks_cluster_security_group_id" {
  description = "ID security group кластера"
  value       = aws_eks_cluster.main.vpc_config[0].cluster_security_group_id
}

output "eks_node_group_id" {
  description = "ID node group"
  value       = aws_eks_node_group.main.id
}

output "eks_node_group_arn" {
  description = "ARN node group"
  value       = aws_eks_node_group.main.arn
}

output "rds_instance_endpoint" {
  description = "Endpoint RDS инстанса"
  value       = aws_db_instance.main.endpoint
}

output "elasticache_endpoint" {
  description = "Endpoint ElastiCache"
  value       = aws_elasticache_cluster.main.endpoint
}

output "load_balancer_dns" {
  description = "DNS балансировщика нагрузки"
  value       = aws_lb.main.dns_name
}

output "cloudwatch_log_group_arn" {
  description = "ARN группы логов CloudWatch"
  value       = var.enable_cloudwatch ? aws_cloudwatch_log_group.main[0].arn : null
}
