package com.mb.ocrservice.service;

import com.mb.ocrservice.dto.DocumentDto;
import com.mb.ocrservice.dto.OcrResultDto;
import com.mb.ocrservice.dto.ValidationResultDto;
import com.mb.ocrservice.model.Document;
import com.mb.ocrservice.model.DocumentType;
import com.mb.ocrservice.model.OcrResult;
import com.mb.ocrservice.model.ValidationResult;
import com.mb.ocrservice.repository.DocumentRepository;
import com.mb.ocrservice.repository.DocumentTypeRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentTypeRepository documentTypeRepository;
    private final DocumentStorageService documentStorageService;
    private final OcrService ocrService;
    private final ValidationService validationService;

    @Autowired
    public DocumentService(
            DocumentRepository documentRepository,
            DocumentTypeRepository documentTypeRepository,
            DocumentStorageService documentStorageService,
            OcrService ocrService,
            ValidationService validationService) {
        this.documentRepository = documentRepository;
        this.documentTypeRepository = documentTypeRepository;
        this.documentStorageService = documentStorageService;
        this.ocrService = ocrService;
        this.validationService = validationService;
    }

    /**
     * Upload and process a document.
     *
     * @param file The document file
     * @param documentTypeName The type of the document
     * @return The created document
     * @throws IOException If an error occurs during file storage
     */
    @Transactional
    public Document uploadAndProcessDocument(MultipartFile file, String documentTypeName) throws IOException {
        // Find document type
        DocumentType documentType = documentTypeRepository.findByName(documentTypeName)
                .orElseThrow(() -> new IllegalArgumentException("Invalid document type: " + documentTypeName));
        
        // Store document file
        String filePath = documentStorageService.storeDocument(file, documentTypeName);
        
        // Create document entity
        Document document = new Document();
        document.setDocumentType(documentType);
        document.setFileName(file.getOriginalFilename());
        document.setFilePath(filePath);
        document.setFileSize(file.getSize());
        document.setMimeType(file.getContentType());
        document.setStatus(Document.Status.PENDING.name());
        
        // Save document
        Document savedDocument = documentRepository.save(document);
        
        // Process document asynchronously
        processDocumentAsync(savedDocument.getId());
        
        return savedDocument;
    }

    /**
     * Process a document asynchronously.
     *
     * @param documentId The ID of the document to process
     * @return A CompletableFuture that will be completed when the processing is done
     */
    public CompletableFuture<ValidationResult> processDocumentAsync(Integer documentId) {
        return ocrService.processDocumentAsync(documentId)
                .thenCompose(ocrResult -> validationService.validateDocumentAsync(documentId));
    }

    /**
     * Get a document by ID.
     *
     * @param id The ID of the document
     * @return The document
     */
    public DocumentDto getDocument(Integer id) {
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Document not found with ID: " + id));
        return convertToDto(document);

    }

    /**
     * Get all documents.
     *
     * @return A list of all documents
     */
    public List<Document> getAllDocuments() {
        return documentRepository.findAll();
    }

    /**
     * Get all documents with pagination.
     *
     * @param pageable The pagination information
     * @return A page of documents
     */
    public Page<Document> getAllDocuments(Pageable pageable) {
        return documentRepository.findAll(pageable);
    }

    /**
     * Get documents by type.
     *
     * @param documentTypeName The type of the documents
     * @return A list of documents of the specified type
     */
    public List<Document> getDocumentsByType(String documentTypeName) {
        DocumentType documentType = documentTypeRepository.findByName(documentTypeName)
                .orElseThrow(() -> new IllegalArgumentException("Invalid document type: " + documentTypeName));
        
        return documentRepository.findByDocumentType(documentType);
    }

    /**
     * Get documents by type with pagination.
     *
     * @param documentTypeName The type of the documents
     * @param pageable The pagination information
     * @return A page of documents of the specified type
     */
    public Page<Document> getDocumentsByType(String documentTypeName, Pageable pageable) {
        DocumentType documentType = documentTypeRepository.findByName(documentTypeName)
                .orElseThrow(() -> new IllegalArgumentException("Invalid document type: " + documentTypeName));
        
        return documentRepository.findByDocumentType(documentType, pageable);
    }

    /**
     * Get documents by status.
     *
     * @param status The status of the documents
     * @return A list of documents with the specified status
     */
    public List<Document> getDocumentsByStatus(String status) {
        return documentRepository.findByStatus(status);
    }

    /**
     * Get documents by status with pagination.
     *
     * @param status The status of the documents
     * @param pageable The pagination information
     * @return A page of documents with the specified status
     */
    public Page<Document> getDocumentsByStatus(String status, Pageable pageable) {
        return documentRepository.findByStatus(status, pageable);
    }

    /**
     * Get all documents as DTOs with pagination.
     *
     * @param pageable The pagination information
     * @return A page of document DTOs
     */
    public Page<DocumentDto> getAllDocumentDtos(Pageable pageable) {
        return documentRepository.findAll(pageable).map(this::convertToDto);
    }

    /**
     * Delete a document.
     *
     * @param id The ID of the document to delete
     * @throws IOException If an error occurs during file deletion
     */
    @Transactional
    public void deleteDocument(Integer id) throws IOException {
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Document not found with ID: " + id));
        
        // Delete document file
        documentStorageService.deleteDocument(document.getFilePath());
        
        // Delete document entity
        documentRepository.delete(document);
    }

    /**
     * Get the OCR result for a document.
     *
     * @param documentId The ID of the document
     * @return The OCR result
     */
    public OcrResultDto getOcrResult(Integer documentId) {
        Optional<OcrResult> ocrResult = ocrService.getOcrResult(documentId);
        return ocrResult.isEmpty() ? new OcrResultDto():  convertToDto(ocrResult.get());
    }

    /**
     * Get the validation result for a document.
     *
     * @param documentId The ID of the document
     * @return The validation result
     */
    public ValidationResultDto getValidationResult(Integer documentId) {
        Optional<ValidationResult> validationResult = validationService.getValidationResult(documentId);
        return validationResult.isEmpty() ? new ValidationResultDto():  convertToDto(validationResult.get());

    }

    /**
     * Convert a Document entity to a DocumentDto.
     *
     * @param document The Document entity
     * @return The DocumentDto
     */
    public DocumentDto convertToDto(Document document) {
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
    public OcrResultDto convertToDto(OcrResult ocrResult) {
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
    public ValidationResultDto convertToDto(ValidationResult validationResult) {
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