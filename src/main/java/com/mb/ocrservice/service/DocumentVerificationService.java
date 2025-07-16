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
    @Transactional
    public void processVerifyDocumentEvent(VerifyDocumentEvent event) {
        log.info("Received verify-document event: {}", event);
        
        try {
            // Create audit log for event received
            createAuditLog("VERIFY_DOCUMENT_EVENT_RECEIVED",
                    "Received verify-document event for application: " + event.getApplicationId() + ", storage: " + event.getStorageId(),
                    event.getApplicationId(),
                    event.getEventId());
            
            // Find PAN card document in the storage directory
            Path storageDir = Paths.get(storageLocation, event.getStorageId());
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
            
            // Save document metadata
            Document savedDocument = documentRepository.save(document);
            
            // Create audit log for document metadata saved
            createAuditLog("DOCUMENT_METADATA_SAVED",
                    "Saved metadata for document: " + savedDocument.getId(),
                    event.getApplicationId(),
                    event.getEventId());
            
            // Process document asynchronously
            CompletableFuture<Void> processingFuture = documentService.processDocumentAsync(savedDocument.getId())
                    .thenAccept(validationResult -> {
                        try {
                            // Get OCR and validation results
                            OcrResultDto ocrResult = documentService.getOcrResult(savedDocument.getId());
                            ValidationResultDto validationResult1 = documentService.getValidationResult(savedDocument.getId());
                            
                            // Create audit log for document processed
                            createAuditLog("DOCUMENT_PROCESSED",
                                    "Processed document: " + savedDocument.getId(),
                                    event.getApplicationId(),
                                    event.getEventId());
                            
                            // Publish document-verification-completed event
                            publishDocumentVerificationCompletedEvent(
                                    event, 
                                    savedDocument, 
                                    ocrResult, 
                                    validationResult1);
                        } catch (Exception e) {
                            log.error("Error processing document verification results", e);
                            
                            // Create audit log for error
                            createAuditLog("DOCUMENT_PROCESSING_ERROR",
                                    "Error processing document: " + e.getMessage(),
                                    event.getApplicationId(),
                                    event.getEventId());
                        }
                    });
            
            // Log that processing has started
            log.info("Started processing document for application: {}, storage: {}", event.getApplicationId(), event.getStorageId());
            
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
     * Publishes a document-verification-completed event to Kafka.
     *
     * @param originalEvent The original verify-document event
     * @param document The document that was processed
     * @param ocrResult The OCR result
     * @param validationResult The validation result
     */
    private void publishDocumentVerificationCompletedEvent(
            VerifyDocumentEvent originalEvent, 
            Document document, 
            OcrResultDto ocrResult, 
            ValidationResultDto validationResult) {
        
        DocumentVerificationCompletedEvent event = DocumentVerificationCompletedEvent.builder()
                .applicationNumber(originalEvent.getApplicationId())
                .storageId(originalEvent.getStorageId())
                .requestId(originalEvent.getEventId())
                .documentId(document.getId())
                .documentType(document.getDocumentType().getName())
                .status(document.getStatus())
                .isAuthentic(validationResult.getAuthentic())
                .isComplete(validationResult.getComplete())
                .confidenceScore(validationResult.getOverallConfidenceScore())
                .rawText(ocrResult.getRawText())
                .extractedData(ocrResult.getStructuredData())
                .verificationDetails(validationResult.getValidationDetails())
                .completedAt(Instant.now().toEpochMilli())
                .build();
        
        kafkaTemplate.send(documentVerificationCompletedTopic, event);
        
        log.info("Published document-verification-completed event for application: {}, storage: {}", originalEvent.getApplicationId(), originalEvent.getStorageId());
        
        // Create audit log for event published
        createAuditLog("DOCUMENT_VERIFICATION_COMPLETED_EVENT_PUBLISHED",
                "Published document-verification-completed event for document: " + document.getId(),
                originalEvent.getApplicationId(),
                originalEvent.getEventId());
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
                .storageId(originalEvent.getStorageId())
                .requestId(originalEvent.getEventId())
                .status(Document.Status.FAILED.name())
                .verificationDetails(createErrorMap(errorMessage))
                .completedAt(Instant.now().toEpochMilli())
                .build();
        
        kafkaTemplate.send(documentVerificationCompletedTopic, event);
        
        log.info("Published error event for application: {}, storage: {}", originalEvent.getApplicationId(), originalEvent.getStorageId());
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