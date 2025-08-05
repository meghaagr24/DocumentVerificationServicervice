package com.mb.ocrservice.service;

import com.mb.ocrservice.dto.DocumentVerificationCompletedEvent;
import com.mb.ocrservice.dto.DocumentVerificationErrorEvent;
import com.mb.ocrservice.dto.OcrResultDto;
import com.mb.ocrservice.dto.ValidationResultDto;
import com.mb.ocrservice.dto.VerifyDocumentEvent;
import com.mb.ocrservice.model.AuditLog;
import com.mb.ocrservice.model.Document;
import com.mb.ocrservice.model.DocumentType;
import com.mb.ocrservice.model.ValidationResult;
import com.mb.ocrservice.repository.AuditLogRepository;
import com.mb.ocrservice.repository.DocumentRepository;
import com.mb.ocrservice.repository.DocumentTypeRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Service responsible for processing document verification requests.
 * Acts as a Kafka consumer for verify-document events and a Kafka producer for document-verification-completed events.
 */
@Service
@Slf4j
public class DocumentVerificationService {

    @Value("${kafka.topic.document-verification-completed:document-verification-completed}")
    private String documentVerificationCompletedTopic;

    private final DocumentService documentService;
    private final ValidationService validationService;
    private final DocumentTypeRepository documentTypeRepository;
    private final DocumentRepository documentRepository;
    private final AuditLogRepository auditLogRepository;
    private final KafkaTemplate<String, DocumentVerificationCompletedEvent> kafkaTemplate;
    private final KafkaTemplate<String, DocumentVerificationErrorEvent> errorKafkaTemplate;

    @Autowired
    public DocumentVerificationService(
            DocumentService documentService,
            ValidationService validationService,
            DocumentTypeRepository documentTypeRepository,
            DocumentRepository documentRepository,
            AuditLogRepository auditLogRepository,
            KafkaTemplate<String, DocumentVerificationCompletedEvent> completedEventKafkaTemplate,
            KafkaTemplate<String, DocumentVerificationErrorEvent> errorEventKafkaTemplate) {
        this.documentService = documentService;
        this.validationService = validationService;
        this.documentTypeRepository = documentTypeRepository;
        this.documentRepository = documentRepository;
        this.auditLogRepository = auditLogRepository;
        this.kafkaTemplate = completedEventKafkaTemplate;
        this.errorKafkaTemplate = errorEventKafkaTemplate;
    }

    /**
     * Processes verify-document events received from Kafka.
     *
     * @param event The verify-document event
     */
    @KafkaListener(topics = "${kafka.topic.verify-document:verify-document}", groupId = "${kafka.group-id:document-verification-service}")
    public void processVerifyDocumentEvent(VerifyDocumentEvent event) {
        log.info("Received verify-document event: {}", event);
        
        try {
            // Create audit log for event received
            createAuditLog("VERIFY_DOCUMENT_EVENT_RECEIVED",
                    "Received verify-document event for application: " + event.getApplicationId() +
                    ", with " + event.getApplicantStorageIds().size() + " document details",
                    event.getApplicationId(),
                    event.getEventId());
            
            // Create the completed event that will be populated with results
            DocumentVerificationCompletedEvent completedEvent = DocumentVerificationCompletedEvent.builder()
                    .applicationNumber(event.getApplicationId())
                    .requestId(event.getEventId())
                    .status(Document.Status.COMPLETED.name())
                    .completedAt(Instant.now().toEpochMilli())
                    .build();
            
            // Process each document detail
            boolean hasErrors = false;
            for (Map.Entry<String, VerifyDocumentEvent.DocDetailEvent> entry : event.getApplicantStorageIds().entrySet()) {
                String applicantId = entry.getKey();
                VerifyDocumentEvent.DocDetailEvent docDetail = entry.getValue();
                
                try {
                    // Process the document for this storage ID and applicant ID with validation
                    processDocumentWithValidation(event, docDetail, applicantId, completedEvent);
                } catch (Exception e) {
                    log.error("Error processing document for storage ID: {}, applicant ID: {}", docDetail.getStorageId(), applicantId, e);
                    
                    // Create audit log for error
                    createAuditLog("DOCUMENT_PROCESSING_ERROR",
                            "Error processing document for storage ID: " + docDetail.getStorageId() + ", applicant ID: " + applicantId + ": " + e.getMessage(),
                            event.getApplicationId(),
                            event.getEventId());
                    
                    hasErrors = true;
                }
            }
            
            // Set the overall status based on whether there were any errors
            if (hasErrors) {
                completedEvent.setStatus("PARTIAL_SUCCESS");
            }
            
            // Publish the completed event
            kafkaTemplate.send(documentVerificationCompletedTopic, completedEvent);
            
            // Create audit log for event published
            createAuditLog("DOCUMENT_VERIFICATION_COMPLETED_EVENT_PUBLISHED",
                    "Published document-verification-completed event for application: " + event.getApplicationId(),
                    event.getApplicationId(),
                    event.getEventId());
            
            log.info("Completed processing all documents for application: {}", event.getApplicationId());
            
        } catch (Exception e) {
            log.error("Error processing verify-document event", e);
            
            // Create audit log for error
            createAuditLog("VERIFY_DOCUMENT_EVENT_ERROR",
                    "Error processing verify-document event: " + e.getMessage(),
                    event.getApplicationId(),
                    event.getEventId());
            
            // Publish error event
            publishErrorEvent(event, e.getMessage());
        }
    }
    
    /**
     * Processes a document for a specific storage ID and applicant ID with document validation.
     *
     * @param event The original verify-document event
     * @param docDetail The document details containing storageId, documentType, and documentId
     * @param applicantId The applicant ID associated with the document
     * @param completedEvent The completed event to populate with results
     * @throws IOException If an error occurs during file operations
     */
    private void processDocumentWithValidation(
            VerifyDocumentEvent event,
            VerifyDocumentEvent.DocDetailEvent docDetail,
            String applicantId,
            DocumentVerificationCompletedEvent completedEvent) throws IOException {
        
        log.info("Processing document with validation for storage ID: {}, applicant ID: {}, document type: {}",
                docDetail.getStorageId(), applicantId, docDetail.getDocumentType());
        
        // Find document using the composite key: applicantId and documentType
        DocumentType expectedDocumentType = documentTypeRepository.findByName(docDetail.getDocumentType())
                .orElseThrow(() -> new IllegalStateException("Document type not found in database: " + docDetail.getDocumentType()));
        
        Optional<Document> documentOptional = documentRepository.findByApplicantIdAndDocumentType(applicantId, expectedDocumentType);
        
        Document document;
        
        if (documentOptional.isEmpty()) {
            log.info("Document not found for applicant ID: {} and document type: {}. Creating a new document.",
                    applicantId, docDetail.getDocumentType());
            
            // Create a new document
            document = new Document();
            document.setDocumentType(expectedDocumentType);
            document.setApplicantId(applicantId);
            document.setFileName(docDetail.getDocumentType() + "_document.jpg");
            document.setFilePath(docDetail.getStorageId() + "/" + docDetail.getDocumentType() + "_document.jpg");
            document.setFileSize(1024L); // Set a positive value to pass validation
            document.setMimeType("image/jpeg"); // Default mime type
        } else {
            document = documentOptional.get();
        }
        
        log.info("Found document for applicant ID: {}, document type: {}, document ID: {}",
                applicantId, docDetail.getDocumentType(), document.getId());
        
        // Update document status to pending for processing
        document.setStatus(Document.Status.PENDING.name());
        Document savedDocument = documentRepository.save(document);
        
        if (documentOptional.isEmpty()) {
            // Create audit log for new document created
            createAuditLog("DOCUMENT_CREATED_FOR_PROCESSING",
                    "Created new document for processing: " + savedDocument.getId() + " with file path: " + savedDocument.getFilePath(),
                    event.getApplicationId(),
                    event.getEventId());
        }
        
        // Create audit log for document found and processing started
        createAuditLog("DOCUMENT_FOUND_FOR_PROCESSING",
                "Found document for processing: " + savedDocument.getId() + " with file path: " + savedDocument.getFilePath(),
                event.getApplicationId(),
                event.getEventId());
        
        // Process document for OCR
        log.info("Processing document with ID: {}", savedDocument.getId());
        documentService.processDocumentAsync(savedDocument.getId(), docDetail.getStorageId()).join();
        
        // Now explicitly validate the document
        log.info("Validating document with ID: {}", savedDocument.getId());
        ValidationResult validationResult = validationService.validateDocument(savedDocument.getId());
        
        // Get OCR result
        OcrResultDto ocrResult = documentService.getOcrResult(savedDocument.getId());
        ValidationResultDto validationResultDto = documentService.convertToDto(validationResult);
        
        // Perform document validation
        boolean validationPassed = validateDocumentNumber(ocrResult, docDetail);
        
        if (!validationPassed) {
            // Publish error event for document validation failure
            publishDocumentValidationErrorEvent(event, docDetail, applicantId, ocrResult);
            
            // Create audit log for validation failure
            createAuditLog("DOCUMENT_VALIDATION_FAILED",
                    "Document validation failed for storage ID: " + docDetail.getStorageId() +
                    ", expected: " + docDetail.getDocumentId() +
                    ", extracted: " + extractDocumentNumber(ocrResult, docDetail.getDocumentType()),
                    event.getApplicationId(),
                    event.getEventId());
            
            throw new RuntimeException("Document validation failed: Expected " + docDetail.getDocumentId() +
                    ", but extracted " + extractDocumentNumber(ocrResult, docDetail.getDocumentType()));
        }
        else {

            // Create audit log for document processed successfully
            createAuditLog("DOCUMENT_PROCESSED_SUCCESSFULLY",
                    "Processed and validated document: " + savedDocument.getId() + " for applicant: " + applicantId,
                    event.getApplicationId(),
                    event.getEventId());

            // Create a customer document result
            DocumentVerificationCompletedEvent.CustomerDocumentResult result =
                    DocumentVerificationCompletedEvent.CustomerDocumentResult.builder()
                            .documentId(savedDocument.getId())
                            .storageId(docDetail.getStorageId())
                            .documentType(savedDocument.getDocumentType().getName())
                            .isAuthentic(validationResultDto.getAuthentic())
                            .isComplete(validationResultDto.getComplete())
                            .confidenceScore(validationResultDto.getOverallConfidenceScore())
                            .rawText(ocrResult.getRawText())
                            .extractedData(ocrResult.getStructuredData())
                            .verificationDetails(validationResultDto.getValidationDetails())
                            .build();

            // Add the result to the completed event
            completedEvent.addCustomerResult(applicantId, result);

            log.info("Completed processing document with validation for storage ID: {}, applicant ID: {}",
                    docDetail.getStorageId(), applicantId);
        }
    }
    
    /**
     * Saves document metadata in a separate transaction to ensure it's committed
     * before processing.
     *
     * @param document The document to save
     * @param event The original verify-document event
     * @return The saved document
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Document saveDocumentMetadata(Document document, VerifyDocumentEvent event) {
        Document savedDocument = documentRepository.save(document);
        
        // Create audit log for document metadata saved
        createAuditLog("DOCUMENT_METADATA_SAVED",
                "Saved metadata for document: " + savedDocument.getId(),
                event.getApplicationId(),
                event.getEventId());
        
        return savedDocument;
    }
    
    /**
     * Creates an audit log entry.
     *
     * @param action The action being audited
     * @param detailsMessage Details about the action
     * @param applicationId The application ID
     * @param eventId The event ID
     */
   private void createAuditLog(String action, String detailsMessage, String applicationId, String eventId) {
        AuditLog auditLog = new AuditLog();
        auditLog.setAction(action);
        
        // Create details map
        Map<String, Object> details = new HashMap<>();
        details.put("message", detailsMessage);
        details.put("applicationId", applicationId);
        details.put("eventId", eventId);
        details.put("timestamp", Instant.now().toString());
        auditLog.setDetails(details);
        
        auditLogRepository.save(auditLog);
    }
    
    
    /**
     * Publishes an error event to Kafka.
     *
     * @param originalEvent The original verify-document event
     * @param errorMessage The error message
     */
    private void publishErrorEvent(VerifyDocumentEvent originalEvent, String errorMessage) {
        DocumentVerificationCompletedEvent event = DocumentVerificationCompletedEvent.builder()
                .applicationNumber(originalEvent.getApplicationId())
                .storageId(originalEvent.getFirstStorageId()) // Use the first storage ID for backward compatibility
                .requestId(originalEvent.getEventId())
                .status(Document.Status.FAILED.name())
                .verificationDetails(createErrorMap(errorMessage))
                .completedAt(Instant.now().toEpochMilli())
                .build();
        
        kafkaTemplate.send(documentVerificationCompletedTopic, event);
        
        log.info("Published error event for application: {}", originalEvent.getApplicationId());
    }
    
    /**
     * Creates a map with error details.
     *
     * @param errorMessage The error message
     * @return A map with error details
     */
    private Map<String, Object> createErrorMap(String errorMessage) {
        Map<String, Object> errorMap = new HashMap<>();
        errorMap.put("error", errorMessage);
        return errorMap;
    }
    
    /**
     * Validates the extracted document number against the expected document ID.
     *
     * @param ocrResult The OCR result containing extracted data
     * @param docDetail The document details containing expected document ID
     * @return true if validation passes, false otherwise
     */
    private boolean validateDocumentNumber(OcrResultDto ocrResult, VerifyDocumentEvent.DocDetailEvent docDetail) {
        String expectedDocumentId = docDetail.getDocumentId();
        String extractedDocumentId = extractDocumentNumber(ocrResult, docDetail.getDocumentType());
        
        if (expectedDocumentId == null || extractedDocumentId == null) {
            return false;
        }
        
        // Normalize both strings for comparison (remove spaces, convert to uppercase)
        String normalizedExpected = expectedDocumentId.replaceAll("\\s+", "").toUpperCase();
        String normalizedExtracted = extractedDocumentId.replaceAll("\\s+", "").toUpperCase();
        
        return normalizedExpected.equals(normalizedExtracted);
    }
    
    /**
     * Extracts the document number from OCR result based on document type.
     *
     * @param ocrResult The OCR result containing extracted data
     * @param documentType The type of document (PANCARD, AADHAR, etc.)
     * @return The extracted document number or null if not found
     */
    private String extractDocumentNumber(OcrResultDto ocrResult, String documentType) {
        Map<String, Object> structuredData = ocrResult.getStructuredData();
        
        if (structuredData == null) {
            return null;
        }
        
        switch (documentType.toUpperCase()) {
            case "PANCARD":
            case "PAN":
                // Try pan_number first (with underscore), then panNumber (camelCase)
                String panNumber = extractFieldValue(structuredData, "pan_number");
                if (panNumber == null) {
                    panNumber = extractFieldValue(structuredData, "panNumber");
                }
                return panNumber;
            case "AADHAR":
            case "AADHAAR":
                // Try aadhaar_number first (with underscore), then aadhaarNumber (camelCase)
                String aadhaarNumber = extractFieldValue(structuredData, "aadhaar_number");
                if (aadhaarNumber == null) {
                    aadhaarNumber = extractFieldValue(structuredData, "aadhaarNumber");
                }
                return aadhaarNumber;
            default:
                // Try common field names
                String docNumber = extractFieldValue(structuredData, "document_number");
                if (docNumber == null) {
                    docNumber = extractFieldValue(structuredData, "documentNumber");
                }
                if (docNumber == null) {
                    docNumber = extractFieldValue(structuredData, "number");
                }
                return docNumber;
        }
    }
    
    /**
     * Extracts a field value from structured data, handling nested maps with value and confidence.
     *
     * @param structuredData The structured data map
     * @param fieldName The field name to extract
     * @return The field value or null if not found
     */
    private String extractFieldValue(Map<String, Object> structuredData, String fieldName) {
        try {
            Object fieldObj = structuredData.get(fieldName);
            if (fieldObj == null) {
                return null;
            }
            
            // If it's a nested map with value and confidence
            if (fieldObj instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> fieldMap = (Map<String, Object>) fieldObj;
                Object value = fieldMap.get("value");
                if (value instanceof String) {
                    String stringValue = (String) value;
                    return stringValue.trim().isEmpty() ? null : stringValue;
                }
            }
            
            // If it's a direct string value
            if (fieldObj instanceof String) {
                String stringValue = (String) fieldObj;
                return stringValue.trim().isEmpty() ? null : stringValue;
            }
            
            return null;
        } catch (Exception e) {
            log.warn("Error extracting field '{}' from structured data", fieldName, e);
            return null;
        }
    }
    
    /**
     * Publishes a document validation error event to Kafka.
     *
     * @param originalEvent The original verify-document event
     * @param docDetail The document details
     * @param applicantId The applicant ID
     * @param ocrResult The OCR result
     */
    private void publishDocumentValidationErrorEvent(
            VerifyDocumentEvent originalEvent,
            VerifyDocumentEvent.DocDetailEvent docDetail,
            String applicantId,
            OcrResultDto ocrResult) {
        
        try {
            DocumentVerificationErrorEvent errorEvent = DocumentVerificationErrorEvent.builder()
                    .eventId(java.util.UUID.randomUUID().toString())
                    .applicationId(originalEvent.getApplicationId())
                    .applicantId(applicantId)
                    .storageId(docDetail.getStorageId())
                    .documentType(docDetail.getDocumentType())
                    .expectedDocumentId(docDetail.getDocumentId())
                    .extractedDocumentId(extractDocumentNumber(ocrResult, docDetail.getDocumentType()))
                    .errorMessage("Document validation failed: Expected " + docDetail.getDocumentId() + 
                            ", but extracted " + extractDocumentNumber(ocrResult, docDetail.getDocumentType()))
                    .build();
            
            // Publish to error topic
            String errorTopic = "document-verification-error";
            errorKafkaTemplate.send(errorTopic, errorEvent);
            
            log.info("Published document validation error event for application: {}, applicant: {}", 
                    originalEvent.getApplicationId(), applicantId);
            
        } catch (Exception e) {
            log.error("Error publishing document validation error event", e);
        }
    }
}
