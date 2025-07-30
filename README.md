# OCR Document Validation Service

A comprehensive document validation service that uses Google Vision OCR to extract information from uploaded documents, validate their authenticity and completeness, and provide confidence scoring for decision-making in the digital lending onboarding process.

## Business Use Case

This service is designed to validate various documents like Aadhar, PAN Card, Driving License, and Bank statements to extract information and provide document authenticity and completeness verification. This is crucial for fraud detection and regulatory compliance in the digital lending onboarding process.

## Supported Document Types

- **Aadhaar Card**: Government-issued identity document with 12-digit unique number
- **PAN Card**: Permanent Account Number card for tax identification
- **Driving License**: State-issued driving authorization document
- **Bank Statement**: Financial institution account statement (generic patterns)

## Architecture Overview

The system consists of the following components:

1. **Backend (Java 17 Spring Boot)**
   - Document upload service
   - Google Vision OCR integration
   - Document classification and validation
   - PostgreSQL database for storing document data and validation results
   - RESTful APIs for frontend and other microservices

2. **Frontend (React.js)**
   - Document upload interface
   - Display of OCR results and validation status
   - User-friendly UI for the digital lending onboarding process

## Key Features

- Document upload and storage
- OCR processing using Google Vision API
- Document classification and validation
- Confidence scoring
- API endpoints for other microservices

## Technology Stack

### Backend
- Java 17
- Spring Boot
- Spring Data JPA
- PostgreSQL
- Google Cloud Vision API

### Frontend
- React.js
- Axios for API calls
- Material-UI for components

## Getting Started

### Prerequisites

- Java 17
- Maven
- PostgreSQL
- Node.js and npm
- Google Cloud account with Vision API enabled

### Backend Setup

1. Clone the repository
2. Configure PostgreSQL database in `application.properties`
3. Set up Google Cloud credentials
4. Run the Spring Boot application:
   ```
   mvn spring-boot:run
   ```

### Running with S3 Storage

The application supports using Amazon S3 for document storage. For local development, you can use LocalStack to emulate S3.

#### Prerequisites
- Docker and Docker Compose
- AWS CLI

#### Using the run-with-s3.sh Script

We provide a convenient script `run-with-s3.sh` that handles setting up LocalStack, creating the S3 bucket, and running the application:

```bash
# Run with local profile (default)
./run-with-s3.sh

# Run with production profile
./run-with-s3.sh --profile prod

# Run tests with local profile
./run-with-s3.sh --action test

# Run tests with production profile
./run-with-s3.sh --action test --profile prod
```

The script accepts the following parameters:
- `--profile`: Can be "local" or "prod" (default is "local")
- `--action`: Can be "run" or "test" (default is "run")

When using the local profile, the script will:
1. Start LocalStack using Docker Compose
2. Initialize the S3 bucket using the `localstack-init/init-s3.sh` script, which:
   - Creates the S3 bucket named "document-verification-service"
   - Sets a bucket policy to allow public read access (for development purposes only)
   - Configures the bucket in the ap-south-1 region
3. Run the application with the local profile
4. Stop LocalStack when the application is stopped

This provides a seamless development experience with S3 storage without needing an actual AWS account.

#### Working with S3 Bucket Files

After running the application with LocalStack, you can use AWS CLI to interact with the S3 bucket:

```bash
# List the S3 bucket contents
aws --endpoint-url=http://localhost:4566 s3 ls s3://document-verification-service/

# List files in a specific folder (e.g., for a specific applicant)
aws --endpoint-url=http://localhost:4566 s3 ls s3://document-verification-service/applicant_123/

# Download a specific file
aws --endpoint-url=http://localhost:4566 s3 cp s3://document-verification-service/applicant_123/document.jpg ./downloaded-document.jpg

# Delete a specific file
aws --endpoint-url=http://localhost:4566 s3 rm s3://document-verification-service/applicant_123/document.jpg
```

#### S3 Storage Configuration

The application supports two storage options:
1. **Local File System**: Default storage mechanism that saves files to the local file system
2. **Amazon S3**: Cloud storage option that saves files to Amazon S3 buckets

For local development with S3 storage (using LocalStack):
- The application uses the configuration in `application-local.properties`
- S3 endpoint is set to `http://localhost:4566` (LocalStack default)
- S3 bucket name is set to `document-verification-service`
- Access key and secret key are both set to `test`

For production deployment with S3:
- Configure the following environment variables:
  - `AWS_REGION`: The AWS region to use (default: ap-south-1)
  - `AWS_S3_BUCKET_NAME`: The name of the S3 bucket to use
  - `AWS_ACCESS_KEY`: (Optional) AWS access key
  - `AWS_SECRET_KEY`: (Optional) AWS secret key

If AWS credentials are not explicitly provided, the application will use the default AWS credential provider chain.

### Frontend Setup

1. Navigate to the frontend directory
2. Install dependencies:
   ```
   npm install
   ```
3. Start the development server:
   ```
   npm start
   ```

## API Endpoints

### Document Upload
- `POST /api/documents/upload`
  - Uploads a document for OCR processing
  - Returns document ID and status

### Document Status
- `GET /api/documents/{id}`
  - Returns the status and results of document processing

### OCR Results
- `GET /api/documents/{id}/ocr-results`
  - Returns the OCR results for a document

### Validation Results
- `GET /api/documents/{id}/validation-results`
  - Returns the validation results for a document

## Database Schema

The database schema includes tables for:
- Users
- Document Types
- Documents
- OCR Results
- Extracted Fields
- Validation Results
- Audit Logs

## Security Considerations

- Secure document storage
- Encryption of sensitive data
- Authentication and authorization for API access
- Secure communication with Google Vision API
- Compliance with data protection regulations

## Troubleshooting

### Common Issues

#### Spring Profile Configuration

- **Issue**: Error message `Property 'spring.profiles.active' imported from location 'class path resource [application-local.properties]' is invalid in a profile specific resource`
- **Solution**: Do not set `spring.profiles.active` in profile-specific properties files (like application-local.properties). Instead, set it in the main application.properties file or via command line arguments.

#### LocalStack Connectivity

- **Issue**: Application cannot connect to LocalStack S3
- **Solution**: 
  1. Verify LocalStack is running: `docker ps | grep localstack`
  2. Check LocalStack logs: `docker logs localstack`
  3. Ensure the S3 bucket exists: `aws --endpoint-url=http://localhost:4566 s3 ls`
  4. Restart LocalStack: `docker-compose down && docker-compose up -d`

#### AWS S3 Connectivity

- **Issue**: Application cannot connect to AWS S3 in production
- **Solution**:
  1. Verify AWS credentials are correctly configured
  2. Check that the S3 bucket exists and is accessible
  3. Verify IAM permissions allow the required S3 operations
  4. Check network connectivity to AWS S3 endpoints

## License

This project is licensed under the MIT License - see the LICENSE file for details.
