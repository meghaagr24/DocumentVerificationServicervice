package com.mb.ocrservice.service;

import com.mb.ocrservice.dto.DocumentVerificationCompletedEvent;
import com.mb.ocrservice.dto.OcrResultDto;
import com.mb.ocrservice.dto.ValidationResultDto;
import com.mb.ocrservice.dto.VerifyDocumentEvent;
import com.mb.ocrservice.model.AuditLog;
import com.mb.ocrservice.model.Document;
import com.mb.ocrservice.model.DocumentType;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Service responsible for processing document verification requests.
 * Acts as a Kafka consumer for verify-document events and a Kafka producer for document-verification-completed events.
 */
@Service
@Slf4j
public class DocumentVerificationService {

    @Value("${document.storage.location}")
    private String storageLocation;

    @Value("${kafka.topic.document-verification-completed:document-verification-completed}")
    private String documentVerificationCompletedTopic;

    private final DocumentService documentService;
    private final DocumentTypeRepository documentTypeRepository;
    private final DocumentRepository documentRepository;
    private final AuditLogRepository auditLogRepository;
    private final KafkaTemplate<String, DocumentVerificationCompletedEvent> kafkaTemplate;

    @Autowired
    public DocumentVerificationService(
            DocumentService documentService,
            DocumentTypeRepository documentTypeRepository,
            DocumentRepository documentRepository,
            AuditLogRepository auditLogRepository,
            KafkaTemplate<String, DocumentVerificationCompletedEvent> kafkaTemplate) {
        this.documentService = documentService;
        this.documentTypeRepository = documentTypeRepository;
        this.documentRepository = documentRepository;
        this.auditLogRepository = auditLogRepository;
        this.kafkaTemplate = kafkaTemplate;
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
                    ", with " + event.getApplicantStorageIds().size() + " storage IDs",
                    event.getApplicationId(),
                    event.getEventId());
            
            // Create the completed event that will be populated with results
            DocumentVerificationCompletedEvent completedEvent = DocumentVerificationCompletedEvent.builder()
                    .applicationNumber(event.getApplicationId())
                    .requestId(event.getEventId())
                    .status(Document.Status.COMPLETED.name())
                    .completedAt(Instant.now().toEpochMilli())
                    .build();
            
            // For backward compatibility
            if (event.getApplicantStorageIds().isEmpty() && event.getStorageId() != null) {
                // Add the storageId to applicantStorageIds with a default customer ID
                event.getApplicantStorageIds().put(event.getStorageId(), "default");
            }
            
            // Process each storage ID
            boolean hasErrors = false;
            for (Map.Entry<String, String> entry : event.getApplicantStorageIds().entrySet()) {
                String customerId = entry.getKey();
                String storageId = entry.getValue();
                
                try {
                    // Process the document for this storage ID and customer ID
                    processDocumentForStorageId(event, storageId, customerId, completedEvent);
                } catch (Exception e) {
                    log.error("Error processing document for storage ID: {}, customer ID: {}", storageId, customerId, e);
                    
                    // Create audit log for error
                    createAuditLog("DOCUMENT_PROCESSING_ERROR",
                            "Error processing document for storage ID: " + storageId + ", customer ID: " + customerId + ": " + e.getMessage(),
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
     * Processes a document for a specific storage ID and customer ID.
     *
     * @param event The original verify-document event
     * @param storageId The storage ID to process
     * @param customerId The customer ID associated with the storage ID
     * @param completedEvent The completed event to populate with results
     * @throws IOException If an error occurs during file operations
     */
    private void processDocumentForStorageId(
            VerifyDocumentEvent event,
            String storageId,
            String customerId,
            DocumentVerificationCompletedEvent completedEvent) throws IOException {
        
        log.info("Processing document for storage ID: {}, customer ID: {}", storageId, customerId);
        
        // Find PAN card document in the storage directory
        Path storageDir = Paths.get(storageLocation, storageId);
        if (!Files.exists(storageDir)) {
            throw new IOException("Storage directory not found: " + storageDir);
        }
        
        // Look for PAN card documents
        Optional<Path> panCardPath = Files.walk(storageDir, 1)
                .filter(path -> !Files.isDirectory(path))
                .filter(path -> path.getFileName().toString().toLowerCase().contains("pan"))
                .findFirst();
        
        if (panCardPath.isEmpty()) {
            throw new IOException("PAN card document not found in storage directory: " + storageDir);
        }
        
        // Get PAN document type
        DocumentType panDocumentType = documentTypeRepository.findByName("PAN")
                .orElseThrow(() -> new IllegalStateException("PAN document type not found in database"));
        
        // Create document entity
        Document document = new Document();
        document.setDocumentType(panDocumentType);
        document.setFileName(panCardPath.get().getFileName().toString());
        document.setFilePath(panCardPath.get().toString());
        document.setFileSize(Files.size(panCardPath.get()));
        document.setMimeType(Files.probeContentType(panCardPath.get()));
        document.setStatus(Document.Status.PENDING.name());
        
        // Save document metadata in a separate transaction to ensure it's committed
        Document savedDocument = saveDocumentMetadata(document, event);
        
        // Process document synchronously, but use the async method from DocumentService
        log.info("Processing document with ID: {}", savedDocument.getId());
        documentService.processDocumentAsync(savedDocument.getId()).join();
        
        // Get OCR and validation results
        OcrResultDto ocrResult = documentService.getOcrResult(savedDocument.getId());
        ValidationResultDto validationResult = documentService.getValidationResult(savedDocument.getId());

        // Create audit log for document processed
        createAuditLog("DOCUMENT_PROCESSED",
                "Processed document: " + savedDocument.getId() + " for customer: " + customerId,
                event.getApplicationId(),
                event.getEventId());
        
        // Create a customer document result
        DocumentVerificationCompletedEvent.CustomerDocumentResult result =
                DocumentVerificationCompletedEvent.CustomerDocumentResult.builder()
                .documentId(savedDocument.getId())
                .storageId(storageId)
                .documentType(savedDocument.getDocumentType().getName())
                .isAuthentic(validationResult.getAuthentic())
                .isComplete(validationResult.getComplete())
                .confidenceScore(validationResult.getOverallConfidenceScore())
                .rawText(ocrResult.getRawText())
                .extractedData(ocrResult.getStructuredData())
                .verificationDetails(validationResult.getValidationDetails())
                .build();
        
        // Add the result to the completed event
        completedEvent.addCustomerResult(customerId, result);
        
        // For backward compatibility, set the fields in the completed event
//        if (completedEvent.getStorageId() == null) {
//            completedEvent.setStorageId(storageId);
//            completedEvent.setDocumentId(savedDocument.getId());
//            completedEvent.setDocumentType(savedDocument.getDocumentType().getName());
//            completedEvent.setIsAuthentic(validationResult.getAuthentic());
//            completedEvent.setIsComplete(validationResult.getComplete());
//            completedEvent.setConfidenceScore(validationResult.getOverallConfidenceScore());
//            completedEvent.setRawText(ocrResult.getRawText());
//            completedEvent.setExtractedData(ocrResult.getStructuredData());
//            completedEvent.setVerificationDetails(validationResult.getValidationDetails());
//        }
//
        log.info("Completed processing document for storage ID: {}, customer ID: {}", storageId, customerId);
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
}