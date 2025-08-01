resource "aws_ecr_repository" "app_repo" {
  name                 = var.ecr_repository_name
  image_tag_mutability = "MUTABLE"

  image_scanning_configuration {
    scan_on_push = true
  }

  tags = {
    Name        = var.ecr_repository_name
    Environment = var.environment
    Project     = var.project_name
  }
}

resource "aws_ecr_lifecycle_policy" "app_repo_policy" {
  repository = aws_ecr_repository.app_repo.name

  policy = jsonencode({
    rules = [
      {
        rulePriority = 1
        description  = "Keep last 5 images"
        selection = {
          tagStatus     = "any"
          countType     = "imageCountMoreThan"
          countNumber   = 5
        }
        action = {
          type = "expire"
        }
      }
    ]
  })
}

# Create an IAM user for CI/CD to push images to ECR
resource "aws_iam_user" "ecr_user" {
  name = "${var.project_name}-${var.environment}-ecr-user"

  tags = {
    Name        = "${var.project_name}-${var.environment}-ecr-user"
    Environment = var.environment
    Project     = var.project_name
  }
}

resource "aws_iam_policy" "ecr_policy" {
  name        = "${var.project_name}-${var.environment}-ecr-policy"
  description = "Policy for pushing images to ECR"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "ecr:GetDownloadUrlForLayer",
          "ecr:BatchGetImage",
          "ecr:BatchCheckLayerAvailability",
          "ecr:PutImage",
          "ecr:InitiateLayerUpload",
          "ecr:UploadLayerPart",
          "ecr:CompleteLayerUpload",
          "ecr:GetAuthorizationToken"
        ]
        Resource = [
          aws_ecr_repository.app_repo.arn
        ]
      },
      {
        Effect = "Allow"
        Action = [
          "ecr:GetAuthorizationToken"
        ]
        Resource = "*"
      }
    ]
  })
}

resource "aws_iam_user_policy_attachment" "ecr_user_policy" {
  user       = aws_iam_user.ecr_user.name
  policy_arn = aws_iam_policy.ecr_policy.arn
}

# Create access key for the IAM user
# Note: In a real-world scenario, you might want to create this outside of Terraform
# or use AWS Secrets Manager to store the credentials
resource "aws_iam_access_key" "ecr_user_key" {
  user = aws_iam_user.ecr_user.name
}

# Create SSM Parameter for the ECR repository URL
resource "aws_ssm_parameter" "ecr_repository_url" {
  name        = "/${var.project_name}/${var.environment}/ecr_repository_url"
  description = "The URL of the ECR repository"
  type        = "String"
  value       = aws_ecr_repository.app_repo.repository_url

  tags = {
    Environment = var.environment
    Project     = var.project_name
  }
}
