variable "aws_region" {
  description = "The AWS region to deploy resources"
  type        = string
  default     = "ap-south-1"
}

variable "environment" {
  description = "Environment name"
  type        = string
  default     = "dev"
}

variable "project_name" {
  description = "Name of the project"
  type        = string
  default     = "document-verification-service"
}

# RDS Variables
variable "db_name" {
  description = "Name of the database"
  type        = string
  default     = "ocr_service"
}

variable "db_username" {
  description = "Username for the database"
  type        = string
  default     = "postgres"
}

variable "db_password" {
  description = "Password for the database"
  type        = string
  default     = "postgres"  # This should be changed in production
}

variable "db_instance_class" {
  description = "Instance class for the RDS instance"
  type        = string
  default     = "db.t3.small"
}

# ECR Variables
variable "ecr_repository_name" {
  description = "Name of the ECR repository"
  type        = string
  default     = "document-verification-service"
}

variable "ecr_image_tag" {
  description = "Tag for the Docker image in ECR"
  type        = string
  default     = "latest"
}

# ECS Variables
variable "app_port" {
  description = "Port the application listens on"
  type        = number
  default     = 8081
}

variable "app_count" {
  description = "Number of application instances to run"
  type        = number
  default     = 1
}

variable "fargate_cpu" {
  description = "CPU units for the Fargate task"
  type        = number
  default     = 512  # 0.5 vCPU
}

variable "fargate_memory" {
  description = "Memory for the Fargate task"
  type        = number
  default     = 1024  # 1 GB
}

# ALB Variables
variable "health_check_path" {
  description = "Path for the ALB health check"
  type        = string
  default     = "/doc-service/actuator/health"
}

# S3 Variables
variable "bucket_name" {
  description = "Base name of the S3 bucket"
  type        = string
  default     = "mbrdi-document-verification-service"
}

# Kafka Topics
variable "kafka_topics" {
  description = "Kafka topics used by the application"
  type        = list(string)
  default     = [
    "verify-document",
    "document-verification-completed"
  ]
}
