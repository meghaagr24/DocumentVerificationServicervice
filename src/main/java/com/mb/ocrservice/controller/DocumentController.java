package com.mb.ocrservice.controller;

import com.mb.ocrservice.dto.DocumentDto;
import com.mb.ocrservice.dto.OcrResultDto;
import com.mb.ocrservice.dto.ValidationResultDto;
import com.mb.ocrservice.exception.DocumentUploadException;
import com.mb.ocrservice.model.Document;
import com.mb.ocrservice.service.DocumentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    /**
     * Upload a document for OCR processing.
     *
     * @param file The document file
     * @param documentType The type of the document
     * @return The created document
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentDto> uploadDocumentWithProcessing(
            @RequestParam("file") MultipartFile file,
            @RequestParam("documentType") String documentType) {
        try {
            Document document = documentService.uploadAndProcessDocument(file, documentType);
            return ResponseEntity.status(HttpStatus.CREATED).body(documentService.convertToDto(document));
        } catch (DocumentUploadException e) {
            log.error("Document upload failed: {}", e.getMessage(), e);
            // The GlobalExceptionHandler will handle this exception and return proper error response
            throw e;
        }
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentDto> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("documentType") String documentType,
            @RequestParam("storageId") String storageId) {

        try {
            Document document = documentService.uploadDocument(file, documentType, storageId);
            return ResponseEntity.status(HttpStatus.CREATED).body(documentService.convertToDto(document));
        } catch (DocumentUploadException e) {
            log.error("Document upload failed: {}", e.getMessage(), e);
            // The GlobalExceptionHandler will handle this exception and return proper error response
            throw e;
        }
    }

//    /**
//     * Get a document by ID.
//     *
//     * @param id The ID of the document
//     * @return The document
//     */
//    @GetMapping("/{id}")
//    public ResponseEntity<DocumentDto> getDocument(@PathVariable Integer id) {
//        try {
//            return ResponseEntity.ok(documentService.getDocument(id));
//        } catch (IllegalArgumentException e) {
//            log.error("Document not found", e);
//            return ResponseEntity.notFound().build();
//        }
//    }

//    /**
//     * Get all documents.
//     *
//     * @param pageable The pagination information
//     * @return A page of documents
//     */
//    @GetMapping
//    public ResponseEntity<Page<DocumentDto>> getAllDocuments(Pageable pageable) {
//        return ResponseEntity.ok(documentService.getAllDocumentDtos(pageable));
//    }

//    /**
//     * Get documents by type.
//     *
//     * @param documentType The type of the documents
//     * @param pageable The pagination information
//     * @return A page of documents of the specified type
//     */
//    @GetMapping("/type/{documentType}")
//    public ResponseEntity<Page<DocumentDto>> getDocumentsByType(
//            @PathVariable String documentType,
//            Pageable pageable) {
//        try {
//            Page<Document> documents = documentService.getDocumentsByType(documentType, pageable);
//            Page<DocumentDto> documentDtos = documents.map(documentService::convertToDto);
//            return ResponseEntity.ok(documentDtos);
//        } catch (IllegalArgumentException e) {
//            log.error("Invalid document type", e);
//            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
//        }
//    }

//    /**
//     * Get documents by status.
//     *
//     * @param status The status of the documents
//     * @param pageable The pagination information
//     * @return A page of documents with the specified status
//     */
//    @GetMapping("/status/{status}")
//    public ResponseEntity<Page<DocumentDto>> getDocumentsByStatus(
//            @PathVariable String status,
//            Pageable pageable) {
//        Page<Document> documents = documentService.getDocumentsByStatus(status, pageable);
//        Page<DocumentDto> documentDtos = documents.map(documentService::convertToDto);
//        return ResponseEntity.ok(documentDtos);
//    }

//    /**
//     * Delete a document.
//     *
//     * @param id The ID of the document to delete
//     * @return A response with no content
//     */
//    @DeleteMapping("/{id}")
//    public ResponseEntity<Void> deleteDocument(@PathVariable Integer id) {
//        try {
//            documentService.deleteDocument(id);
//            return ResponseEntity.noContent().build();
//        } catch (IllegalArgumentException e) {
//            log.error("Document not found", e);
//            return ResponseEntity.notFound().build();
//        } catch (IOException e) {
//            log.error("Failed to delete document", e);
//            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
//        }
//    }

//    /**
//     * Get the OCR result for a document.
//     *
//     * @param id The ID of the document
//     * @return The OCR result
//     */
//    @GetMapping("/{id}/ocr-result")
//    public ResponseEntity<OcrResultDto> getOcrResult(@PathVariable Integer id) {
//        return ResponseEntity.ok(documentService.getOcrResult(id));
//    }
//
//    /**
//     * Get the validation result for a document.
//     *
//     * @param id The ID of the document
//     * @return The validation result
//     */
//    @GetMapping("/{id}/validation-result")
//    public ResponseEntity<ValidationResultDto> getValidationResult(@PathVariable Integer id) {
//        return ResponseEntity.ok(documentService.getValidationResult(id));
//    }

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