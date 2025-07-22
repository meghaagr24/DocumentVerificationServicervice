package com.mb.ocrservice.exception;

/**
 * Exception thrown when an error occurs during document upload.
 */
public class DocumentUploadException extends RuntimeException {

    private final String errorCode;
    private final String documentType;
    private final String fileName;

    public DocumentUploadException(String message) {
        super(message);
        this.errorCode = "DOCUMENT_UPLOAD_ERROR";
        this.documentType = null;
        this.fileName = null;
    }

    public DocumentUploadException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
        this.documentType = null;
        this.fileName = null;
    }

    public DocumentUploadException(String message, String errorCode, String documentType, String fileName) {
        super(message);
        this.errorCode = errorCode;
        this.documentType = documentType;
        this.fileName = fileName;
    }

    public DocumentUploadException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = "DOCUMENT_UPLOAD_ERROR";
        this.documentType = null;
        this.fileName = null;
    }

    public DocumentUploadException(String message, String errorCode, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.documentType = null;
        this.fileName = null;
    }

    public DocumentUploadException(String message, String errorCode, String documentType, String fileName, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.documentType = documentType;
        this.fileName = fileName;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getDocumentType() {
        return documentType;
    }

    public String getFileName() {
        return fileName;
    }
} 