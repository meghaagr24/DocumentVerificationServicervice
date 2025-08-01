# Document Verification Service

This directory contains Terraform configurations for deploying the Document Verification Service to AWS ECS Fargate with a PostgreSQL database and S3 storage.

## Components

- **RDS PostgreSQL**: A managed PostgreSQL database for storing application data.
- **S3 Bucket**: A storage bucket for document files.
- **ECR Repository**: A Docker image repository for the service.
- **ECS Service**: A Fargate service running the containerized application.
- **ALB Target Group**: A target group for routing traffic to the service.

## Prerequisites

- The core infrastructure must be deployed first.
- Docker must be installed for building and pushing the container image.

## Usage

1. Initialize Terraform:
   ```bash
   terraform init
   ```

2. Review the plan:
   ```bash
   terraform plan
   ```

3. Apply the configuration:
   ```bash
   terraform apply
   ```

## Deploying the Application

After the infrastructure is deployed, you need to build and push the Docker image to ECR:

1. Build the Docker image:
   ```bash
   docker build -t document-verification-service .
   ```

2. Tag and push the image to ECR (use the commands from the terraform output):
   ```bash
   terraform output -raw ecr_push_commands
   ```

3. Update the ECS service to use the new image:
   ```bash
   terraform output -raw update_service_command
   ```

## Environment Variables

The ECS task definition includes the following environment variables:

- `SPRING_DATASOURCE_URL`: JDBC URL for the PostgreSQL database
- `SPRING_DATASOURCE_USERNAME`: Database username
- `SPRING_DATASOURCE_PASSWORD`: Database password
- `SPRING_KAFKA_BOOTSTRAP_SERVERS`: Kafka bootstrap servers
- `KAFKA_TOPIC_VERIFY_DOCUMENT`: Kafka topic for document verification requests
- `KAFKA_TOPIC_DOCUMENT_VERIFICATION_COMPLETED`: Kafka topic for completed verifications
- `SPRING_PROFILES_ACTIVE`: Spring profile (dev, staging, prod)
- `AWS_REGION`: AWS region for S3 access
- `AWS_S3_BUCKET_NAME`: S3 bucket name for document storage
- `GOOGLE_CREDENTIALS_SECRET_ARN`: ARN of the AWS Secrets Manager secret containing Google Cloud Vision API credentials

## Google Cloud Vision API Credentials

The Document Verification Service uses Google Cloud Vision API for OCR processing. The credentials are securely stored in AWS Secrets Manager and accessed by the application at runtime. The Terraform configuration:

1. Creates an AWS Secrets Manager secret containing the Google Cloud Vision API credentials
2. Grants the ECS task role permission to access the secret
3. Provides the secret ARN to the application via an environment variable

The application fetches the credentials from AWS Secrets Manager using the provided ARN through the `GoogleVisionConfigWithSecrets` class. This class:

1. Injects the secret ARN from the environment variable
2. Uses the AWS SDK to fetch the secret value from AWS Secrets Manager
3. Creates a Google Cloud Vision API client with the credentials
4. Makes the client available as a Spring bean for use by the OCR service

This approach ensures that sensitive credentials are not embedded in the Docker image or exposed in the ECS task definition.

### Local Development and Testing

For local development and testing, the application uses a different configuration class (`GoogleVisionConfig`) that:

1. Loads credentials from a local file specified by the `google.vision.credentials-file-path` property
2. Falls back to a mock client if the credentials file is not found
3. Is only active in non-production environments (default, local, dev, test)

This dual configuration approach ensures that:
- In production, credentials are securely fetched from AWS Secrets Manager
- In local development and testing, credentials can be loaded from a local file or mocked
- No changes to existing local development workflows are required

## Service Endpoints

The service exposes REST APIs for document processing and verification:

- `POST /api/documents`: Upload and process a document
- `POST /api/documents/upload`: Upload a document without processing
- `GET /api/documents/{id}`: Get a document by ID
- `GET /api/documents/{id}/ocr-result`: Get OCR result for a document
- `GET /api/documents/{id}/validation-result`: Get validation result for a document
- `GET /api/documents/image/{storageId}/type/{documentType}`: Get document image

## Integration with Loan Application Service

The Document Verification Service is integrated with the Loan Application Service through:

1. **REST API Calls**: The Loan Application Service calls the Document Verification Service endpoints.
2. **Kafka Events**: The services communicate asynchronously via Kafka topics.

## Notes

- The current configuration is optimized for a development environment.
- For production, consider enabling Multi-AZ for RDS, automated backups, and auto-scaling for ECS.
