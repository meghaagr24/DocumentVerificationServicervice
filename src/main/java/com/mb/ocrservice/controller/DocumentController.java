package com.mb.ocrservice.controller;

import com.mb.ocrservice.dto.DocumentDto;
import com.mb.ocrservice.dto.OcrResultDto;
import com.mb.ocrservice.dto.ValidationResultDto;
import com.mb.ocrservice.model.Document;
import com.mb.ocrservice.model.OcrResult;
import com.mb.ocrservice.model.ValidationResult;
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
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

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
    public ResponseEntity<DocumentDto> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam("documentType") String documentType) {
        try {
            Document document = documentService.uploadAndProcessDocument(file, documentType);
            return ResponseEntity.status(HttpStatus.CREATED).body(convertToDto(document));
        } catch (IOException e) {
            log.error("Failed to upload document", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (IllegalArgumentException e) {
            log.error("Invalid document type", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    /**
     * Get a document by ID.
     *
     * @param id The ID of the document
     * @return The document
     */
    @GetMapping("/{id}")
    public ResponseEntity<DocumentDto> getDocument(@PathVariable Integer id) {
        try {
            Document document = documentService.getDocument(id);
            return ResponseEntity.ok(convertToDto(document));
        } catch (IllegalArgumentException e) {
            log.error("Document not found", e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Get all documents.
     *
     * @param pageable The pagination information
     * @return A page of documents
     */
    @GetMapping
    public ResponseEntity<Page<DocumentDto>> getAllDocuments(Pageable pageable) {
        Page<Document> documents = documentService.getAllDocuments(pageable);
        Page<DocumentDto> documentDtos = documents.map(this::convertToDto);
        return ResponseEntity.ok(documentDtos);
    }

    /**
     * Get documents by type.
     *
     * @param documentType The type of the documents
     * @param pageable The pagination information
     * @return A page of documents of the specified type
     */
    @GetMapping("/type/{documentType}")
    public ResponseEntity<Page<DocumentDto>> getDocumentsByType(
            @PathVariable String documentType,
            Pageable pageable) {
        try {
            Page<Document> documents = documentService.getDocumentsByType(documentType, pageable);
            Page<DocumentDto> documentDtos = documents.map(this::convertToDto);
            return ResponseEntity.ok(documentDtos);
        } catch (IllegalArgumentException e) {
            log.error("Invalid document type", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    /**
     * Get documents by status.
     *
     * @param status The status of the documents
     * @param pageable The pagination information
     * @return A page of documents with the specified status
     */
    @GetMapping("/status/{status}")
    public ResponseEntity<Page<DocumentDto>> getDocumentsByStatus(
            @PathVariable String status,
            Pageable pageable) {
        Page<Document> documents = documentService.getDocumentsByStatus(status, pageable);
        Page<DocumentDto> documentDtos = documents.map(this::convertToDto);
        return ResponseEntity.ok(documentDtos);
    }

    /**
     * Delete a document.
     *
     * @param id The ID of the document to delete
     * @return A response with no content
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDocument(@PathVariable Integer id) {
        try {
            documentService.deleteDocument(id);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            log.error("Document not found", e);
            return ResponseEntity.notFound().build();
        } catch (IOException e) {
            log.error("Failed to delete document", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get the OCR result for a document.
     *
     * @param id The ID of the document
     * @return The OCR result
     */
    @GetMapping("/{id}/ocr-result")
    public ResponseEntity<OcrResultDto> getOcrResult(@PathVariable Integer id) {
        Optional<OcrResult> ocrResult = documentService.getOcrResult(id);
        return ocrResult
                .map(result -> ResponseEntity.ok(convertToDto(result)))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get the validation result for a document.
     *
     * @param id The ID of the document
     * @return The validation result
     */
    @GetMapping("/{id}/validation-result")
    public ResponseEntity<ValidationResultDto> getValidationResult(@PathVariable Integer id) {
        Optional<ValidationResult> validationResult = documentService.getValidationResult(id);
        return validationResult
                .map(result -> ResponseEntity.ok(convertToDto(result)))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Convert a Document entity to a DocumentDto.
     *
     * @param document The Document entity
     * @return The DocumentDto
     */
    private DocumentDto convertToDto(Document document) {
        DocumentDto dto = new DocumentDto();
        dto.setId(document.getId());
        dto.setFileName(document.getFileName());
        dto.setFileSize(document.getFileSize());
        dto.setMimeType(document.getMimeType());
        dto.setStatus(document.getStatus());
        dto.setDocumentType(document.getDocumentType().getName());
        dto.setCreatedAt(document.getCreatedAt());
        dto.setUpdatedAt(document.getUpdatedAt());
        return dto;
    }

    /**
     * Convert an OcrResult entity to an OcrResultDto.
     *
     * @param ocrResult The OcrResult entity
     * @return The OcrResultDto
     */
    private OcrResultDto convertToDto(OcrResult ocrResult) {
        OcrResultDto dto = new OcrResultDto();
        dto.setId(ocrResult.getId());
        dto.setDocumentId(ocrResult.getDocument().getId());
        dto.setRawText(ocrResult.getRawText());
        dto.setStructuredData(ocrResult.getStructuredData());
        dto.setConfidenceScore(ocrResult.getConfidenceScore());
        dto.setProcessingTime(ocrResult.getProcessingTime());
        dto.setCreatedAt(ocrResult.getCreatedAt());
        return dto;
    }

    /**
     * Convert a ValidationResult entity to a ValidationResultDto.
     *
     * @param validationResult The ValidationResult entity
     * @return The ValidationResultDto
     */
    private ValidationResultDto convertToDto(ValidationResult validationResult) {
        ValidationResultDto dto = new ValidationResultDto();
        dto.setId(validationResult.getId());
        dto.setDocumentId(validationResult.getDocument().getId());
        dto.setAuthentic(validationResult.getIsAuthentic());
        dto.setComplete(validationResult.getIsComplete());
        dto.setOverallConfidenceScore(validationResult.getOverallConfidenceScore());
        dto.setValidationDetails(validationResult.getValidationDetails());
        dto.setCreatedAt(validationResult.getCreatedAt());
        return dto;
    }
}