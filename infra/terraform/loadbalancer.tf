# ACM Certificate
resource "aws_acm_certificate" "main" {
  domain_name       = var.domain_name
  validation_method = "DNS"
  
  subject_alternative_names = [
    "*.${var.domain_name}",
    "api.${var.domain_name}",
    "ws.${var.domain_name}",
    "dashboard.${var.domain_name}"
  ]
  
  tags = merge(
    var.tags,
    {
      Name = "${var.project_name}-${var.environment}-cert"
    }
  )
  
  lifecycle {
    create_before_destroy = true
  }
}

# DNS Validation
resource "aws_route53_record" "cert_validation" {
  for_each = {
    for dvo in aws_acm_certificate.main.domain_validation_options : dvo.domain_name => {
      name   = dvo.resource_record_name
      record = dvo.resource_record_value
      type   = dvo.resource_record_type
    }
  }
  
  allow_overwrite = true
  name            = each.value.name
  records         = [each.value.record]
  ttl             = 60
  type            = each.value.type
  zone_id         = aws_route53_zone.main.zone_id
}

# Wait for certificate validation
resource "aws_acm_certificate_validation" "main" {
  certificate_arn         = aws_acm_certificate.main.arn
  validation_record_fqdns = [for record in aws_route53_record.cert_validation : record.fqdn]
}

# Route53 Zone
resource "aws_route53_zone" "main" {
  name = var.domain_name
  
  tags = merge(
    var.tags,
    {
      Name = "${var.project_name}-${var.environment}-zone"
    }
  )
}

# Security Group for Load Balancer
resource "aws_security_group" "lb" {
  name        = "${var.project_name}-${var.environment}-lb-sg"
  description = "Security group for Load Balancer"
  vpc_id      = aws_vpc.main.id
  
  ingress {
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }
  
  ingress {
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }
  
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
  
  tags = merge(
    var.tags,
    {
      Name = "${var.project_name}-${var.environment}-lb-sg"
    }
  )
}

# Application Load Balancer
resource "aws_lb" "main" {
  name               = "${var.project_name}-${var.environment}-lb"
  internal           = false
  load_balancer_type = "application"
  security_groups    = [aws_security_group.lb.id]
  subnets            = aws_subnet.public[*].id
  
  enable_deletion_protection = var.environment == "prod"
  
  tags = merge(
    var.tags,
    {
      Name = "${var.project_name}-${var.environment}-lb"
    }
  )
}

# Target Group for API Gateway
resource "aws_lb_target_group" "api_gateway" {
  name        = "${var.project_name}-${var.environment}-api-gateway-tg"
  port        = 3000
  protocol    = "HTTP"
  vpc_id      = aws_vpc.main.id
  target_type = "ip"
  
  health_check {
    path                = "/health"
    interval            = 30
    timeout             = 5
    healthy_threshold   = 2
    unhealthy_threshold = 3
  }
  
  tags = var.tags
}

# Target Group for Ingest WebSocket
resource "aws_lb_target_group" "ingest_ws" {
  name        = "${var.project_name}-${var.environment}-ingest-ws-tg"
  port        = 8080
  protocol    = "HTTP"
  vpc_id      = aws_vpc.main.id
  target_type = "ip"
  
  health_check {
    path                = "/health"
    interval            = 30
    timeout             = 5
    healthy_threshold   = 2
    unhealthy_threshold = 3
  }
  
  tags = var.tags
}

# Target Group for Web Dashboard
resource "aws_lb_target_group" "web_dashboard" {
  name        = "${var.project_name}-${var.environment}-web-dashboard-tg"
  port        = 3000
  protocol    = "HTTP"
  vpc_id      = aws_vpc.main.id
  target_type = "ip"
  
  health_check {
    path                = "/"
    interval            = 30
    timeout             = 5
    healthy_threshold   = 2
    unhealthy_threshold = 3
  }
  
  tags = var.tags
}

# Target Group for Replay Service
resource "aws_lb_target_group" "replay_service" {
  name        = "${var.project_name}-${var.environment}-replay-service-tg"
  port        = 8084
  protocol    = "HTTP"
  vpc_id      = aws_vpc.main.id
  target_type = "ip"
  
  health_check {
    path                = "/health"
    interval            = 30
    timeout             = 5
    healthy_threshold   = 2
    unhealthy_threshold = 3
  }
  
  tags = var.tags
}

# HTTP Listener
resource "aws_lb_listener" "http" {
  load_balancer_arn = aws_lb.main.arn
  port              = 80
  protocol          = "HTTP"
  
  default_action {
    type = "redirect"
    
    redirect {
      port        = "443"
      protocol    = "HTTPS"
      status_code = "301"
    }
  }
}

# HTTPS Listener
resource "aws_lb_listener" "https" {
  load_balancer_arn = aws_lb.main.arn
  port              = 443
  protocol          = "HTTPS"
  certificate_arn   = aws_acm_certificate_validation.main.certificate_arn
  
  default_action {
    type = "fixed-response"
    
    fixed_response {
      content_type = "text/plain"
      message_body = "RidePulse API"
      status_code  = "200"
    }
  }
}

# Listener Rules
resource "aws_lb_listener_rule" "api_gateway" {
  listener_arn = aws_lb_listener.https.arn
  priority     = 100
  
  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.api_gateway.arn
  }
  
  condition {
    path_pattern {
      values = ["/api/*"]
    }
  }
}

resource "aws_lb_listener_rule" "ingest_ws" {
  listener_arn = aws_lb_listener.https.arn
  priority     = 101
  
  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.ingest_ws.arn
  }
  
  condition {
    path_pattern {
      values = ["/ws/*"]
    }
  }
}

resource "aws_lb_listener_rule" "web_dashboard" {
  listener_arn = aws_lb_listener.https.arn
  priority     = 102
  
  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.web_dashboard.arn
  }
  
  condition {
    host_header {
      values = ["dashboard.${var.domain_name}"]
    }
  }
}

resource "aws_lb_listener_rule" "replay_service" {
  listener_arn = aws_lb_listener.https.arn
  priority     = 103
  
  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.replay_service.arn
  }
  
  condition {
    path_pattern {
      values = ["/replay/*"]
    }
  }
}
