# Database outputs
output "db_instance_endpoint" {
  description = "The connection endpoint for the RDS instance"
  value       = aws_db_instance.postgres.endpoint
  sensitive   = true
}

output "db_instance_name" {
  description = "The database name"
  value       = aws_db_instance.postgres.db_name
  sensitive   = true
}

# ECR outputs
output "ecr_repository_url" {
  description = "The URL of the ECR repository"
  value       = aws_ecr_repository.app_repo.repository_url
}

# ECS outputs
output "ecs_service_name" {
  description = "The name of the ECS service"
  value       = aws_ecs_service.app.name
}

output "ecs_task_definition_arn" {
  description = "The ARN of the ECS task definition"
  value       = aws_ecs_task_definition.app.arn
}

# ALB outputs
output "target_group_arn" {
  description = "The ARN of the target group"
  value       = aws_lb_target_group.app.arn
}

output "service_url" {
  description = "The URL of the service"
  value       = "http://${local.alb_dns_name}/doc-service/documents"
  sensitive   = true
}

# S3 outputs
output "s3_bucket_name" {
  description = "The name of the S3 bucket"
  value       = "${var.bucket_name}-${var.environment}"
}

output "s3_bucket_arn" {
  description = "The ARN of the S3 bucket"
  value       = "arn:aws:s3:::${var.bucket_name}-${var.environment}"
}

# Deployment commands
output "ecr_push_commands" {
  description = "Commands to build and push the Docker image to ECR"
  value       = <<EOF
# Login to ECR
aws ecr get-login-password --region ${var.aws_region} | docker login --username AWS --password-stdin ${aws_ecr_repository.app_repo.repository_url}

# Build the Docker image
docker build -t ${aws_ecr_repository.app_repo.repository_url}:${var.ecr_image_tag} .

# Push the Docker image to ECR
docker push ${aws_ecr_repository.app_repo.repository_url}:${var.ecr_image_tag}
EOF
  sensitive   = true
}

output "update_service_command" {
  description = "Command to update the ECS service"
  value       = "aws ecs update-service --cluster ${local.ecs_cluster_name} --service ${aws_ecs_service.app.name} --force-new-deployment --region ${var.aws_region}"
  sensitive   = true
}

# IAM user for CI/CD
output "ecr_user_access_key_id" {
  description = "The access key ID for the ECR user"
  value       = aws_iam_access_key.ecr_user_key.id
}

output "ecr_user_secret_access_key" {
  description = "The secret access key for the ECR user"
  value       = aws_iam_access_key.ecr_user_key.secret
  sensitive   = true
}
