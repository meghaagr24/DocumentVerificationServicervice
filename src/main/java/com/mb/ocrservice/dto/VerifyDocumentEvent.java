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
@AllArgsConstructor
public class VerifyDocumentEvent {
    
    /**
     * A unique identifier for the verification event.
     */
    private String eventId;
    
    /**
     * The application ID associated with the document verification request.
     * This is used to locate the documents in the storage directory.
     */
    private String applicationId;
    
    /**
     * The timestamp when the event was created.
     */
    private String timestamp;
}