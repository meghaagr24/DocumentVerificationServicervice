package com.mb.ocrservice.controller;

import com.mb.ocrservice.dto.DocumentDto;
import com.mb.ocrservice.exception.DocumentUploadException;
import com.mb.ocrservice.model.Document;
import com.mb.ocrservice.service.DocumentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/documents")
@Slf4j
public class DocumentController {

    private final DocumentService documentService;

    @Autowired
    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

//    /**
//     * Upload a document for OCR processing.
//     *
//     * @param file The document file
//     * @param documentType The type of the document
//     * @return The created document
//     */
//    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
//    public ResponseEntity<DocumentDto> uploadDocumentWithProcessing(
//            @RequestParam("file") MultipartFile file,
//            @RequestParam("documentType") String documentType) {
//        try {
//            Document document = documentService.uploadAndProcessDocument(file, documentType);
//            return ResponseEntity.status(HttpStatus.CREATED).body(documentService.convertToDto(document));
//        } catch (DocumentUploadException e) {
//            log.error("Document upload failed: {}", e.getMessage(), e);
//            // The GlobalExceptionHandler will handle this exception and return proper error response
//            throw e;
//        }
//    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentDto> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("documentType") String documentType,
            @RequestParam("applicantId") String applicantId,
            @RequestParam("storageId") String storageId) {

        try {
            Document document = documentService.uploadDocument(file, documentType, storageId, applicantId);
            return ResponseEntity.status(HttpStatus.CREATED).body(documentService.convertToDto(document));
        } catch (DocumentUploadException e) {
            log.error("Document upload failed: {}", e.getMessage(), e);
            // The GlobalExceptionHandler will handle this exception and return proper error response
            throw e;
        }
    }

    /**
     * Get document image by storage ID and document type.
     *
     * @param storageId The storage ID
     * @param documentType The document type
     * @return The document image as a byte array
     */
    @GetMapping("/image/{storageId}/type/{documentType}")
    public ResponseEntity<byte[]> getDocumentImageByStorageIdAndType(
            @PathVariable String storageId,
            @PathVariable String documentType) {
        try {
            byte[] imageData = documentService.getDocumentImageByStorageIdAndType(storageId, documentType);
            
            // Determine content type based on document type (assuming most are images)
            String contentType = "image/jpeg"; // Default to JPEG
            
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header("Content-Disposition", "inline; filename=\"" + documentType + "_document.jpg\"")
                    .body(imageData);
        } catch (IllegalArgumentException e) {
            log.error("Document not found", e);
            return ResponseEntity.notFound().build();
        } catch (IOException e) {
            log.error("Failed to read document image", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

}