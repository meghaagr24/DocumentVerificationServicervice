resource "aws_db_subnet_group" "postgres" {
  name       = "${var.project_name}-${var.environment}-db-subnet-group"
  subnet_ids = local.private_subnet_ids

  tags = {
    Name        = "${var.project_name}-${var.environment}-db-subnet-group"
    Environment = var.environment
    Project     = var.project_name
  }
}

resource "aws_db_parameter_group" "postgres" {
  name   = "${var.project_name}-${var.environment}-db-parameter-group"
  family = "postgres16"

  parameter {
    name  = "log_connections"
    value = "1"
  }

  parameter {
    name  = "log_disconnections"
    value = "1"
  }

  parameter {
    name  = "log_statement"
    value = "ddl"
  }

  parameter {
    name  = "log_min_duration_statement"
    value = "1000"  # Log statements that take more than 1 second
  }

  tags = {
    Name        = "${var.project_name}-${var.environment}-db-parameter-group"
    Environment = var.environment
    Project     = var.project_name
  }
}

resource "aws_db_instance" "postgres" {
  identifier             = "${var.project_name}-${var.environment}-db"
  engine                 = "postgres"
  engine_version         = "16.3"
  instance_class         = var.db_instance_class
  allocated_storage      = 20
  storage_type           = "gp2"
  db_name                = var.db_name
  username               = var.db_username
  password               = var.db_password
  db_subnet_group_name   = aws_db_subnet_group.postgres.name
  vpc_security_group_ids = [local.db_security_group_id]
  parameter_group_name   = aws_db_parameter_group.postgres.name
  publicly_accessible    = false
  skip_final_snapshot    = true
  
  # Disable Multi-AZ and backups for dev environment
  multi_az               = false
  backup_retention_period = 0
  
  # Enable deletion protection in production
  deletion_protection    = var.environment == "prod" ? true : false

  # Enable monitoring
  monitoring_interval    = 60
  monitoring_role_arn    = aws_iam_role.rds_monitoring_role.arn
  
  tags = {
    Name        = "${var.project_name}-${var.environment}-db"
    Environment = var.environment
    Project     = var.project_name
  }
}

resource "aws_iam_role" "rds_monitoring_role" {
  name = "${var.project_name}-${var.environment}-rds-monitoring-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Principal = {
          Service = "monitoring.rds.amazonaws.com"
        }
      }
    ]
  })

  tags = {
    Name        = "${var.project_name}-${var.environment}-rds-monitoring-role"
    Environment = var.environment
    Project     = var.project_name
  }
}

resource "aws_iam_role_policy_attachment" "rds_monitoring_attachment" {
  role       = aws_iam_role.rds_monitoring_role.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonRDSEnhancedMonitoringRole"
}

# CloudWatch Log Group for RDS logs removed to reduce costs
# resource "aws_cloudwatch_log_group" "rds_logs" {
#   name              = "/aws/rds/instance/${var.project_name}-${var.environment}-db/postgresql"
#   retention_in_days = 7
#
#   tags = {
#     Environment = var.environment
#     Project     = var.project_name
#   }
# }

# Create SSM Parameter for the database endpoint
resource "aws_ssm_parameter" "db_endpoint" {
  name        = "/${var.project_name}/${var.environment}/db_endpoint"
  description = "The endpoint of the RDS instance"
  type        = "String"
  value       = aws_db_instance.postgres.endpoint

  tags = {
    Environment = var.environment
    Project     = var.project_name
  }
}

# Create SSM Parameter for the database name
resource "aws_ssm_parameter" "db_name" {
  name        = "/${var.project_name}/${var.environment}/db_name"
  description = "The name of the database"
  type        = "String"
  value       = aws_db_instance.postgres.db_name

  tags = {
    Environment = var.environment
    Project     = var.project_name
  }
}
