package com.mb.ocrservice.exception;

import com.mb.ocrservice.dto.ErrorResponseDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Global exception handler for the application.
 */
@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * Handles DocumentUploadException.
     */
    @ExceptionHandler(DocumentUploadException.class)
    public ResponseEntity<ErrorResponseDto> handleDocumentUploadException(
            DocumentUploadException ex, WebRequest request) {
        
        log.error("Document upload failed: {}", ex.getMessage(), ex);
        
        Map<String, Object> additionalInfo = new HashMap<>();
        if (ex.getDocumentType() != null) {
            additionalInfo.put("documentType", ex.getDocumentType());
        }
        if (ex.getFileName() != null) {
            additionalInfo.put("fileName", ex.getFileName());
        }
        
        ErrorResponseDto errorResponse = ErrorResponseDto.of(
                ex.getErrorCode(),
                "Document upload failed",
                ex.getMessage(),
                request.getDescription(false),
                additionalInfo
        );
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    /**
     * Handles OcrProcessingException.
     */
    @ExceptionHandler(OcrProcessingException.class)
    public ResponseEntity<ErrorResponseDto> handleOcrProcessingException(
            OcrProcessingException ex, WebRequest request) {
        
        log.error("OCR processing failed: {}", ex.getMessage(), ex);
        
        ErrorResponseDto errorResponse = ErrorResponseDto.of(
                "OCR_PROCESSING_ERROR",
                "OCR processing failed",
                ex.getMessage(),
                request.getDescription(false)
        );
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }

    /**
     * Handles IllegalArgumentException.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponseDto> handleIllegalArgumentException(
            IllegalArgumentException ex, WebRequest request) {
        
        log.error("Invalid argument: {}", ex.getMessage(), ex);
        
        ErrorResponseDto errorResponse = ErrorResponseDto.of(
                "INVALID_ARGUMENT",
                "Invalid argument provided",
                ex.getMessage(),
                request.getDescription(false)
        );
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    /**
     * Handles IOException.
     */
    @ExceptionHandler(IOException.class)
    public ResponseEntity<ErrorResponseDto> handleIOException(
            IOException ex, WebRequest request) {
        
        log.error("IO operation failed: {}", ex.getMessage(), ex);
        
        ErrorResponseDto errorResponse = ErrorResponseDto.of(
                "IO_ERROR",
                "File operation failed",
                ex.getMessage(),
                request.getDescription(false)
        );
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }

    /**
     * Handles MaxUploadSizeExceededException.
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponseDto> handleMaxUploadSizeExceededException(
            MaxUploadSizeExceededException ex, WebRequest request) {
        
        log.error("File size exceeded limit: {}", ex.getMessage(), ex);
        
        ErrorResponseDto errorResponse = ErrorResponseDto.of(
                "FILE_SIZE_EXCEEDED",
                "File size exceeds the maximum allowed limit",
                "The uploaded file is too large. Please use a smaller file.",
                request.getDescription(false)
        );
        
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(errorResponse);
    }

    /**
     * Handles generic RuntimeException.
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponseDto> handleRuntimeException(
            RuntimeException ex, WebRequest request) {
        
        log.error("Runtime error occurred: {}", ex.getMessage(), ex);
        
        ErrorResponseDto errorResponse = ErrorResponseDto.of(
                "RUNTIME_ERROR",
                "An unexpected error occurred",
                "Please try again later. If the problem persists, contact support.",
                request.getDescription(false)
        );
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }

    /**
     * Handles generic Exception.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDto> handleGenericException(
            Exception ex, WebRequest request) {
        
        log.error("Unexpected error occurred: {}", ex.getMessage(), ex);
        
        ErrorResponseDto errorResponse = ErrorResponseDto.of(
                "INTERNAL_SERVER_ERROR",
                "An unexpected error occurred",
                "Please try again later. If the problem persists, contact support.",
                request.getDescription(false)
        );
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }
} 