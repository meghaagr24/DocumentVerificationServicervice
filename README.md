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

## License

This project is licensed under the MIT License - see the LICENSE file for details.