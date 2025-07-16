package com.mb.ocrservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

/**
 * DTO representing the result of a document verification process.
 * This event is produced to Kafka after document verification is completed.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentVerificationCompletedEvent {
    
    /**
     * The application number associated with the document verification request.
     */
    private String applicationNumber;
    
    /**
     * The storage ID used to locate the documents in the storage directory.
     * This is the folder name in the document storage location.
     */
    private String storageId;
    
    /**
     * The ID of the user who initiated the document verification request.
     */
    private Integer userId;
    
    /**
     * The request ID from the original verification request, used for correlation.
     */
    private String requestId;
    
    /**
     * The ID of the document that was verified.
     */
    private Integer documentId;
    
    /**
     * The type of document that was verified (e.g., "PAN", "AADHAAR").
     */
    private String documentType;
    
    /**
     * The verification status (e.g., "COMPLETED", "FAILED").
     */
    private String status;
    
    /**
     * Flag indicating whether the document is authentic.
     */
    private Boolean isAuthentic;
    
    /**
     * Flag indicating whether the document data is complete.
     */
    private Boolean isComplete;
    
    /**
     * The overall confidence score of the verification.
     */
    private BigDecimal confidenceScore;
    
    /**
     * The raw text extracted from the document.
     */
    private String rawText;
    
    /**
     * Structured data extracted from the document (e.g., PAN number, name).
     */
    private Map<String, Object> extractedData;
    
    /**
     * Additional details about the verification process.
     */
    private Map<String, Object> verificationDetails;
    
    /**
     * Timestamp when the verification was completed.
     */
    private Long completedAt;
}