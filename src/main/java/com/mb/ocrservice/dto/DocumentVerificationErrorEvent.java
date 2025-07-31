package com.mb.ocrservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * DTO representing a document verification error event.
 * This event is published when document validation fails.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentVerificationErrorEvent {
    
    /**
     * A unique identifier for the error event.
     */
    private String eventId;
    
    /**
     * The application ID associated with the document verification request.
     */
    private String applicationId;
    
    /**
     * The applicant ID for whom the document verification failed.
     */
    private String applicantId;
    
    /**
     * The storage ID where the document was stored.
     */
    private String storageId;
    
    /**
     * The type of document that failed verification.
     */
    private String documentType;
    
    /**
     * The expected document ID.
     */
    private String expectedDocumentId;
    
    /**
     * The actual document ID extracted from the document.
     */
    private String extractedDocumentId;
    
    /**
     * The error message describing the validation failure.
     */
    private String errorMessage;
    
    /**
     * The timestamp when the error event was created.
     */
    private Long timestamp;
    
    /**
     * Creates a new DocumentVerificationErrorEvent with current timestamp.
     *
     * @return a new DocumentVerificationErrorEvent
     */
    public static DocumentVerificationErrorEventBuilder builder() {
        return new DocumentVerificationErrorEventBuilder()
                .timestamp(Instant.now().toEpochMilli());
    }
} 