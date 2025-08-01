# AWS Secrets Manager for storing Google Cloud Vision API credentials

# Create a secret for the Google Cloud Vision API credentials
resource "aws_secretsmanager_secret" "google_credentials" {
  name        = "${var.project_name}-${var.environment}-google-credentials"
  description = "Google Cloud Vision API credentials for OCR processing"

  tags = {
    Name        = "${var.project_name}-${var.environment}-google-credentials"
    Environment = var.environment
    Project     = var.project_name
  }
}

# Store the Google Cloud Vision API credentials in the secret
resource "aws_secretsmanager_secret_version" "google_credentials" {
  secret_id     = aws_secretsmanager_secret.google_credentials.id
  secret_string = file("${path.module}/../google-credentials.json")
}

# Grant the ECS task role access to the secret
resource "aws_iam_policy" "secrets_access_policy" {
  name        = "${var.project_name}-${var.environment}-secrets-access-policy"
  description = "Policy for accessing Google Cloud Vision API credentials"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
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

resource "aws_iam_role_policy_attachment" "secrets_access_policy" {
  role       = aws_iam_role.app_task_role.name
  policy_arn = aws_iam_policy.secrets_access_policy.arn
}

# Create SSM Parameter for the secret ARN
resource "aws_ssm_parameter" "google_credentials_secret_arn" {
  name        = "/${var.project_name}/${var.environment}/google_credentials_secret_arn"
  description = "The ARN of the Google Cloud Vision API credentials secret"
  type        = "String"
  value       = aws_secretsmanager_secret.google_credentials.arn

  tags = {
    Environment = var.environment
    Project     = var.project_name
  }
}
