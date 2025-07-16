package com.mb.ocrservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO representing a request to verify a document.
 * This event is consumed from Kafka to trigger document verification.
 */
@Data
@NoArgsConstructor
public class VerifyDocumentEvent {
    
    /**
     * A unique identifier for the verification event.
     */
    private String eventId;
    
    /**
     * The application ID associated with the document verification request.
     */
    private String applicationId;
    
    /**
     * The storage ID used to locate the documents in the storage directory.
     * This is the folder name in the document storage location.
     */
    private String storageId;
    
    /**
     * The timestamp when the event was created.
     */
    private String timestamp;
    
    /**
     * Constructor with all fields.
     *
     * @param eventId The event ID
     * @param applicationId The application ID
     * @param storageId The storage ID
     * @param timestamp The timestamp
     */
    public VerifyDocumentEvent(String eventId, String applicationId, String storageId, String timestamp) {
        this.eventId = eventId;
        this.applicationId = applicationId;
        this.storageId = storageId;
        this.timestamp = timestamp;
    }
    
    /**
     * Constructor without storageId for backward compatibility.
     * Sets storageId equal to applicationId.
     *
     * @param eventId The event ID
     * @param applicationId The application ID
     * @param timestamp The timestamp
     */
    public VerifyDocumentEvent(String eventId, String applicationId, String timestamp) {
        this.eventId = eventId;
        this.applicationId = applicationId;
        this.storageId = applicationId; // Default to applicationId for backward compatibility
        this.timestamp = timestamp;
    }
    /**
     * The application storage ID associated with the document verification request.
     * This is used to locate the documents in the storage directory.
     */
     private String applicantStorageId;
}