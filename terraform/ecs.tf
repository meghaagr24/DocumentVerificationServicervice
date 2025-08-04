# Task definition for the document verification service
resource "aws_ecs_task_definition" "app" {
  family                   = "${var.project_name}-${var.environment}"
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]
  cpu                      = var.fargate_cpu
  memory                   = var.fargate_memory
  execution_role_arn       = local.ecs_task_execution_role_arn
  task_role_arn            = aws_iam_role.app_task_role.arn

  container_definitions = jsonencode([
    {
      name      = "${var.project_name}-${var.environment}"
      image     = "${aws_ecr_repository.app_repo.repository_url}:${var.ecr_image_tag}"
      essential = true
      
      portMappings = [
        {
          containerPort = var.app_port
          hostPort      = var.app_port
          protocol      = "tcp"
        }
      ]
      
      environment = [
        {
          name  = "SPRING_DATASOURCE_URL"
          value = "jdbc:postgresql://${aws_db_instance.postgres.endpoint}/${var.db_name}"
        },
        {
          name  = "SPRING_DATASOURCE_USERNAME"
          value = var.db_username
        },
        {
          name  = "SPRING_DATASOURCE_PASSWORD"
          value = var.db_password
        },
        {
          name  = "SPRING_KAFKA_BOOTSTRAP_SERVERS"
          value = local.kafka_bootstrap_brokers
        },
        {
          name  = "KAFKA_TOPIC_VERIFY_DOCUMENT"
          value = var.kafka_topics[0]
        },
        {
          name  = "KAFKA_TOPIC_DOCUMENT_VERIFICATION_COMPLETED"
          value = var.kafka_topics[1]
        },
        {
          name  = "SPRING_PROFILES_ACTIVE"
          value = var.environment
        },
        {
          name  = "AWS_REGION"
          value = var.aws_region
        },
        {
          name  = "AWS_S3_BUCKET_NAME"
          value = "${var.bucket_name}-${var.environment}"
        },
        {
          name  = "GOOGLE_CREDENTIALS_SECRET_ARN"
          value = aws_secretsmanager_secret.google_credentials.arn
        }
      ]
      
      # logConfiguration removed to reduce CloudWatch costs
      # logConfiguration = {
      #   logDriver = "awslogs"
      #   options = {
      #     "awslogs-group"         = local.ecs_logs_group_name
      #     "awslogs-region"        = var.aws_region
      #     "awslogs-stream-prefix" = var.project_name
      #   }
      # }
      
      healthCheck = {
        command     = ["CMD-SHELL", "curl -f http://localhost:${var.app_port}${var.health_check_path} || exit 1"]
        interval    = 30
        timeout     = 5
        retries     = 3
        startPeriod = 60
      }
    }
  ])

  tags = {
    Name        = "${var.project_name}-${var.environment}"
    Environment = var.environment
    Project     = var.project_name
  }
}

# Service-specific task role with additional permissions
resource "aws_iam_role" "app_task_role" {
  name = "${var.project_name}-${var.environment}-task-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Principal = {
          Service = "ecs-tasks.amazonaws.com"
        }
      }
    ]
  })

  tags = {
    Name        = "${var.project_name}-${var.environment}-task-role"
    Environment = var.environment
    Project     = var.project_name
  }
}

# Service-specific policy for the task role
resource "aws_iam_policy" "app_task_policy" {
  name        = "${var.project_name}-${var.environment}-task-policy"
  description = "Policy for ${var.project_name} ECS task"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      # CloudWatch logs permissions removed to reduce costs
      # {
      #   Effect = "Allow"
      #   Action = [
      #     "logs:CreateLogStream",
      #     "logs:PutLogEvents"
      #   ]
      #   Resource = "arn:aws:logs:${var.aws_region}:*:log-group:${local.ecs_logs_group_name}:*"
      # },
      {
        Effect = "Allow"
        Action = [
          "kafka:DescribeCluster",
          "kafka:GetBootstrapBrokers",
          "kafka:ListTopics",
          "kafka:DescribeTopic"
        ]
        Resource = "*"
      },
      {
        Effect = "Allow"
        Action = [
          "s3:GetObject",
          "s3:PutObject",
          "s3:DeleteObject",
          "s3:ListBucket"
        ]
        Resource = [
          "arn:aws:s3:::${var.bucket_name}-${var.environment}",
          "arn:aws:s3:::${var.bucket_name}-${var.environment}/*"
        ]
      },
      {
        Effect = "Allow"
        Action = [
          "secretsmanager:GetSecretValue",
          "secretsmanager:DescribeSecret"
        ]
        Resource = [
          aws_secretsmanager_secret.google_credentials.arn
        ]
      }
    ]
  })
}

resource "aws_iam_role_policy_attachment" "app_task_policy" {
  role       = aws_iam_role.app_task_role.name
  policy_arn = aws_iam_policy.app_task_policy.arn
}

# ECS Service
resource "aws_ecs_service" "app" {
  name                               = "${var.project_name}-${var.environment}-service"
  cluster                            = local.ecs_cluster_arn
  task_definition                    = aws_ecs_task_definition.app.arn
  desired_count                      = var.app_count
  launch_type                        = "FARGATE"
  scheduling_strategy                = "REPLICA"
  health_check_grace_period_seconds  = 60
  
  network_configuration {
    security_groups  = [local.ecs_security_group_id]
    subnets          = local.private_subnet_ids
    assign_public_ip = false
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.app.arn
    container_name   = "${var.project_name}-${var.environment}"
    container_port   = var.app_port
  }

  # Ignore changes to desired_count because we're not using auto-scaling
  lifecycle {
    ignore_changes = [desired_count]
  }

  depends_on = [
    aws_lb_listener_rule.app,
    aws_lb_listener_rule.app_specific
  ]

  tags = {
    Name        = "${var.project_name}-${var.environment}-service"
    Environment = var.environment
    Project     = var.project_name
  }
}

# Target group for the ALB
resource "aws_lb_target_group" "app" {
  name        = "${var.project_name}-tg"
  port        = var.app_port
  protocol    = "HTTP"
  vpc_id      = local.vpc_id
  target_type = "ip"

  health_check {
    healthy_threshold   = 3
    unhealthy_threshold = 3
    timeout             = 5
    interval            = 30
    path                = var.health_check_path
    port                = "traffic-port"
    matcher             = "200"
  }

  tags = {
    Name        = "${var.project_name}-${var.environment}-tg"
    Environment = var.environment
    Project     = var.project_name
  }
}

# Listener rule for the ALB - Main rule with higher priority than loan service
resource "aws_lb_listener_rule" "app" {
  listener_arn = local.http_listener_arn
  priority     = 90  # Higher priority than loan service (which is 100)

  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.app.arn
  }

  condition {
    path_pattern {
      values = ["/doc-service/*"]
    }
  }
}

# Additional listener rule with even higher priority for specific endpoints
resource "aws_lb_listener_rule" "app_specific" {
  listener_arn = local.http_listener_arn
  priority     = 80  # Even higher priority for specific endpoints

  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.app.arn
  }

  condition {
    path_pattern {
      values = ["/doc-service/documents/upload", "/doc-service/documents/image/*"]
    }
  }
}
