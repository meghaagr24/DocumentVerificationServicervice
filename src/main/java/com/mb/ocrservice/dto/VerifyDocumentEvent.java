package com.mb.ocrservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

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
     * @deprecated Use applicantStorageIds instead
     */
    @Deprecated
    private String storageId;
    
    /**
     * Map of storage IDs to customer IDs.
     * Each storage ID is a folder name in the document storage location.
     * Each customer ID identifies the person associated with the documents.
     * Example: {"7b188fa2-93dd-4591-8a32-3061e55ce598":"applicant_232R"}
     */
    private Map<String, String> applicantStorageIds = new HashMap<>();
    
    /**
     * The timestamp when the event was created.
     */
    private String timestamp;
    
    /**
     * Constructor with all fields including applicantStorageIds.
     *
     * @param eventId The event ID
     * @param applicationId The application ID
     * @param applicantStorageIds Map of storage IDs to customer IDs
     * @param timestamp The timestamp
     */
    public VerifyDocumentEvent(String eventId, String applicationId, Map<String, String> applicantStorageIds, String timestamp) {
        this.eventId = eventId;
        this.applicationId = applicationId;
        this.applicantStorageIds = applicantStorageIds;
        this.timestamp = timestamp;
    }
    
    /**
     * Constructor with single storageId for backward compatibility.
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
        
        // Add the storageId to applicantStorageIds with a default customer ID
        this.applicantStorageIds.put(storageId, "default");
        
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
        
        // Add the applicationId as storageId to applicantStorageIds with a default customer ID
        this.applicantStorageIds.put(applicationId, "default");
        
        this.timestamp = timestamp;
    }
    
    /**
     * Gets the first storage ID from applicantStorageIds or falls back to the deprecated storageId.
     * This method is for backward compatibility.
     *
     * @return The first storage ID or the deprecated storageId
     */
    public String getFirstStorageId() {
        if (applicantStorageIds != null && !applicantStorageIds.isEmpty()) {
            return applicantStorageIds.keySet().iterator().next();
        }
        return storageId;
    }
}