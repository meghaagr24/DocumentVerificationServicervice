# Fetch core infrastructure values from SSM parameters

# VPC
data "aws_ssm_parameter" "vpc_id" {
  name = "/core/${var.environment}/vpc_id"
}

# Subnets
data "aws_ssm_parameter" "private_subnets" {
  name = "/core/${var.environment}/private_subnets"
}

data "aws_ssm_parameter" "public_subnets" {
  name = "/core/${var.environment}/public_subnets"
}

# Security Groups
data "aws_ssm_parameter" "ecs_security_group_id" {
  name = "/core/${var.environment}/ecs_security_group_id"
}

data "aws_ssm_parameter" "db_security_group_id" {
  name = "/core/${var.environment}/db_security_group_id"
}

# ECS
data "aws_ssm_parameter" "ecs_cluster_arn" {
  name = "/core/${var.environment}/ecs_cluster_arn"
}

data "aws_ssm_parameter" "ecs_cluster_name" {
  name = "/core/${var.environment}/ecs_cluster_name"
}

data "aws_ssm_parameter" "ecs_task_execution_role_arn" {
  name = "/core/${var.environment}/ecs_task_execution_role_arn"
}

data "aws_ssm_parameter" "ecs_logs_group_name" {
  name = "/core/${var.environment}/ecs_logs_group_name"
}

# ALB
data "aws_ssm_parameter" "alb_dns_name" {
  name = "/core/${var.environment}/alb_dns_name"
}

data "aws_ssm_parameter" "http_listener_arn" {
  name = "/core/${var.environment}/http_listener_arn"
}

# Kafka
data "aws_ssm_parameter" "kafka_bootstrap_brokers" {
  name = "/core/${var.environment}/kafka_bootstrap_brokers"
}

# Local values for easier reference
locals {
  vpc_id                    = data.aws_ssm_parameter.vpc_id.value
  private_subnet_ids        = split(",", data.aws_ssm_parameter.private_subnets.value)
  public_subnet_ids         = split(",", data.aws_ssm_parameter.public_subnets.value)
  ecs_security_group_id     = data.aws_ssm_parameter.ecs_security_group_id.value
  db_security_group_id      = data.aws_ssm_parameter.db_security_group_id.value
  ecs_cluster_arn           = data.aws_ssm_parameter.ecs_cluster_arn.value
  ecs_cluster_name          = data.aws_ssm_parameter.ecs_cluster_name.value
  ecs_task_execution_role_arn = data.aws_ssm_parameter.ecs_task_execution_role_arn.value
  ecs_logs_group_name       = data.aws_ssm_parameter.ecs_logs_group_name.value
  alb_dns_name              = data.aws_ssm_parameter.alb_dns_name.value
  http_listener_arn         = data.aws_ssm_parameter.http_listener_arn.value
  kafka_bootstrap_brokers   = data.aws_ssm_parameter.kafka_bootstrap_brokers.value
}
