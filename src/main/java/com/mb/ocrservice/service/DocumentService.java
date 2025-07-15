package com.mb.ocrservice.service;

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
    public Document getDocument(Integer id) {
        return documentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Document not found with ID: " + id));
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
    public Optional<OcrResult> getOcrResult(Integer documentId) {
        return ocrService.getOcrResult(documentId);
    }

    /**
     * Get the validation result for a document.
     *
     * @param documentId The ID of the document
     * @return The validation result
     */
    public Optional<ValidationResult> getValidationResult(Integer documentId) {
        return validationService.getValidationResult(documentId);
    }
}