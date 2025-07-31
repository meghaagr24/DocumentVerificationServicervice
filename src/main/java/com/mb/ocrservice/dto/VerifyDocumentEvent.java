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
     * Inner class representing document details for verification.
     */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class DocDetailEvent {
        /**
         * The storage ID (folder name) where documents are stored.
         */
        private String storageId;
        
        /**
         * The type of document (e.g., "PANCARD", "AADHAR").
         */
        private String documentType;
        
        /**
         * The expected document ID (e.g., PAN number, Aadhaar number).
         */
        private String documentId;
    }
    
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
     * Map of applicant IDs to document details.
     * Each applicant ID maps to document details containing storageId, documentType, and documentId.
     * Example: {"7b188fa2-93dd-4591-8a32-3061e55ce598": {"storageId": "applicant_232R", "documentType": "PANCARD", "documentId": "ABCDE1234F"}}
     */
    private Map<String, DocDetailEvent> applicantStorageIds = new HashMap<>();
    
    /**
     * The timestamp when the event was created.
     */
    private String timestamp;
    
    /**
     * Constructor with all fields including applicantStorageIds.
     *
     * @param eventId The event ID
     * @param applicationId The application ID
     * @param applicantStorageIds Map of applicant IDs to document details
     * @param timestamp The timestamp
     */
    public VerifyDocumentEvent(String eventId, String applicationId, Map<String, DocDetailEvent> applicantStorageIds, String timestamp) {
        this.eventId = eventId;
        this.applicationId = applicationId;
        this.applicantStorageIds = applicantStorageIds;
        this.timestamp = timestamp;
    }
    
//    /**
//     * Constructor with single storageId for backward compatibility.
//     *
//     * @param eventId The event ID
//     * @param applicationId The application ID
//     * @param storageId The storage ID
//     * @param timestamp The timestamp
//     */
//    public VerifyDocumentEvent(String eventId, String applicationId, String storageId, String timestamp) {
//        this.eventId = eventId;
//        this.applicationId = applicationId;
//        this.storageId = storageId;
//
//        // Add the storageId to applicantStorageIds with a default customer ID
//        DocDetailEvent docDetail = new DocDetailEvent(storageId, "UNKNOWN", null);
//        this.applicantStorageIds.put("default", docDetail);
//
//        this.timestamp = timestamp;
//    }

//    /**
//     * Constructor without storageId for backward compatibility.
//     * Sets storageId equal to applicationId.
//     *
//     * @param eventId The event ID
//     * @param applicationId The application ID
//     * @param timestamp The timestamp
//     */
//    public VerifyDocumentEvent(String eventId, String applicationId, String timestamp) {
//        this.eventId = eventId;
//        this.applicationId = applicationId;
//        this.storageId = applicationId; // Default to applicationId for backward compatibility
//
//        // Add the applicationId as storageId to applicantStorageIds with a default customer ID
//        DocDetailEvent docDetail = new DocDetailEvent(applicationId, "UNKNOWN", null);
//        this.applicantStorageIds.put("default", docDetail);
//
//        this.timestamp = timestamp;
//    }
    
    /**
     * Gets the first storage ID from applicantStorageIds or falls back to the deprecated storageId.
     * This method is for backward compatibility.
     *
     * @return The first storage ID or the deprecated storageId
     */
    public String getFirstStorageId() {
        if (applicantStorageIds != null && !applicantStorageIds.isEmpty()) {
            return applicantStorageIds.values().iterator().next().getStorageId();
        }
        return storageId;
    }
}