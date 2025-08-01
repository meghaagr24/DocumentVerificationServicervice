terraform {
  backend "s3" {
    # These values should be provided via terraform init -backend-config
    # bucket         = "terraform-state-bucket"
    # key            = "document-verification-service/terraform.tfstate"
    # region         = "ap-south-1"
    # dynamodb_table = "terraform-locks"
    # encrypt        = true
  }
}

provider "aws" {
  region = var.aws_region
}

# Create a random string for unique naming
resource "random_string" "suffix" {
  length  = 8
  special = false
  upper   = false
}

locals {
  name_suffix = "${var.project_name}-${var.environment}-${random_string.suffix.result}"
  full_bucket_name = "${var.bucket_name}-${var.environment}"
}

# CloudWatch Log Group for application logs
resource "aws_cloudwatch_log_group" "app_logs" {
  name              = "/aws/ecs/${var.project_name}-${var.environment}"
  retention_in_days = 7

  tags = {
    Environment = var.environment
    Project     = var.project_name
  }
}

# Create SSM Parameters for sharing outputs with other stacks
resource "aws_ssm_parameter" "service_name" {
  name        = "/${var.project_name}/${var.environment}/service_name"
  description = "The name of the ECS service"
  type        = "String"
  value       = "${var.project_name}-${var.environment}-service"

  tags = {
    Environment = var.environment
    Project     = var.project_name
  }
}

resource "aws_ssm_parameter" "service_url" {
  name        = "/${var.project_name}/${var.environment}/service_url"
  description = "The URL of the service"
  type        = "String"
  value       = "http://${local.alb_dns_name}/api/documents"

  tags = {
    Environment = var.environment
    Project     = var.project_name
  }
}

resource "aws_ssm_parameter" "bucket_name" {
  name        = "/${var.project_name}/${var.environment}/bucket_name"
  description = "The name of the S3 bucket"
  type        = "String"
  value       = "${var.bucket_name}-${var.environment}"

  tags = {
    Environment = var.environment
    Project     = var.project_name
  }
}
