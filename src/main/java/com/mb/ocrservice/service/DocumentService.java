package com.mb.ocrservice.service;

import com.mb.ocrservice.dto.DocumentDto;
import com.mb.ocrservice.dto.OcrResultDto;
import com.mb.ocrservice.dto.ValidationResultDto;
import com.mb.ocrservice.exception.DocumentUploadException;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
     * @throws DocumentUploadException If an error occurs during document upload
     */
    @Transactional
    public Document uploadAndProcessDocument(MultipartFile file, String documentTypeName) throws DocumentUploadException {
        try {
            // Validate file
            validateFile(file);
            
            // Find document type
            DocumentType documentType = documentTypeRepository.findByName(documentTypeName)
                    .orElseThrow(() -> new DocumentUploadException(
                            "Document type '" + documentTypeName + "' is not supported",
                            "INVALID_DOCUMENT_TYPE",
                            documentTypeName,
                            file.getOriginalFilename()
                    ));
            
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
            
            log.info("Document uploaded and processing started successfully: {}", savedDocument.getId());
            return savedDocument;
            
        } catch (DocumentUploadException e) {
            throw e; // Re-throw DocumentUploadException as-is
        } catch (IOException e) {
            log.error("Failed to store document file: {}", e.getMessage(), e);
            throw new DocumentUploadException(
                    "Failed to store document file: " + e.getMessage(),
                    "FILE_STORAGE_ERROR",
                    documentTypeName,
                    file.getOriginalFilename(),
                    e
            );
        } catch (Exception e) {
            log.error("Unexpected error during document upload: {}", e.getMessage(), e);
            throw new DocumentUploadException(
                    "Unexpected error during document upload: " + e.getMessage(),
                    "UPLOAD_ERROR",
                    documentTypeName,
                    file.getOriginalFilename(),
                    e
            );
        }
    }

    /**
     * Validates the uploaded file.
     *
     * @param file The file to validate
     * @throws DocumentUploadException If the file is invalid
     */
    private void validateFile(MultipartFile file) throws DocumentUploadException {
        if (file == null) {
            throw new DocumentUploadException(
                    "No file provided",
                    "NO_FILE_PROVIDED"
            );
        }
        
        if (file.isEmpty()) {
            throw new DocumentUploadException(
                    "Cannot upload empty file",
                    "EMPTY_FILE",
                    null,
                    file.getOriginalFilename()
            );
        }
        
        if (file.getOriginalFilename() == null || file.getOriginalFilename().trim().isEmpty()) {
            throw new DocumentUploadException(
                    "File name is required",
                    "INVALID_FILENAME"
            );
        }
        
        // Validate filename for security (prevent directory traversal and other security issues)
        String fileName = file.getOriginalFilename();
        if (fileName != null && !isValidFileName(fileName)) {
            throw new DocumentUploadException(
                    "Invalid file name. File name contains invalid characters or is too long",
                    "INVALID_FILENAME",
                    null,
                    fileName
            );
        }
        
        // Check file size (10MB limit as configured in application.properties)
        long maxFileSize = 10 * 1024 * 1024; // 10MB in bytes
        if (file.getSize() > maxFileSize) {
            throw new DocumentUploadException(
                    "File size exceeds the maximum allowed limit of 10MB",
                    "FILE_SIZE_EXCEEDED",
                    null,
                    file.getOriginalFilename()
            );
        }
        
        // Validate file extension
        if (fileName != null && !isValidFileExtension(fileName)) {
            throw new DocumentUploadException(
                    "File type not supported. Please upload PDF, JPG, JPEG, or PNG files only",
                    "UNSUPPORTED_FILE_TYPE",
                    null,
                    fileName
            );
        }
        
        // Validate MIME type
        String contentType = file.getContentType();
        if (contentType != null && !isValidMimeType(contentType)) {
            throw new DocumentUploadException(
                    "File content type not supported. Please upload valid document files",
                    "INVALID_CONTENT_TYPE",
                    null,
                    fileName
            );
        }
    }

    /**
     * Checks if the file extension is valid.
     *
     * @param fileName The file name to check
     * @return true if the file extension is valid, false otherwise
     */
    private boolean isValidFileExtension(String fileName) {
        String[] allowedExtensions = {".pdf", ".jpg", ".jpeg", ".png"};
        String lowerFileName = fileName.toLowerCase();
        
        for (String extension : allowedExtensions) {
            if (lowerFileName.endsWith(extension)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if the filename is valid and secure.
     *
     * @param fileName The file name to check
     * @return true if the filename is valid, false otherwise
     */
    private boolean isValidFileName(String fileName) {
        // Check for null or empty
        if (fileName == null || fileName.trim().isEmpty()) {
            return false;
        }
        
        // Check length (max 255 characters)
        if (fileName.length() > 255) {
            return false;
        }
        
        // Check for directory traversal attempts
        if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
            return false;
        }
        
        // Check for invalid characters
        String invalidChars = "<>:\"|?*";
        for (char c : invalidChars.toCharArray()) {
            if (fileName.indexOf(c) != -1) {
                return false;
            }
        }
        
        // Check for control characters
        for (char c : fileName.toCharArray()) {
            if (Character.isISOControl(c)) {
                return false;
            }
        }
        
        return true;
    }

    /**
     * Checks if the MIME type is valid.
     *
     * @param contentType The content type to check
     * @return true if the MIME type is valid, false otherwise
     */
    private boolean isValidMimeType(String contentType) {
        String[] allowedMimeTypes = {
            "application/pdf",
            "image/jpeg",
            "image/jpg",
            "image/png"
        };
        
        for (String allowedType : allowedMimeTypes) {
            if (allowedType.equalsIgnoreCase(contentType)) {
                return true;
            }
        }
        return false;
    }

    @Transactional
    public Document uploadDocument(MultipartFile file, String documentTypeName, String storageId) throws DocumentUploadException {
        try {
            // Validate file
            validateFile(file);
            
            // Find document type
            DocumentType documentType = documentTypeRepository.findByName(documentTypeName)
                    .orElseThrow(() -> new DocumentUploadException(
                            "Document type '" + documentTypeName + "' is not supported",
                            "INVALID_DOCUMENT_TYPE",
                            documentTypeName,
                            file.getOriginalFilename()
                    ));

            // Store document file
            String filePath = documentStorageService.storeDocument(file, documentTypeName, storageId);

            // Create document entity
            Document document = new Document();
            document.setDocumentType(documentType);
            document.setFileName(file.getOriginalFilename());
            document.setFilePath(filePath);
            document.setFileSize(file.getSize());
            document.setMimeType(file.getContentType());
            document.setStatus(Document.Status.UPLOADED.name());
            
            // Save document
            Document savedDocument = documentRepository.save(document);

            log.info("Document uploaded successfully: {}", savedDocument.getId());
            return savedDocument;
            
        } catch (DocumentUploadException e) {
            throw e; // Re-throw DocumentUploadException as-is
        } catch (IOException e) {
            log.error("Failed to store document file: {}", e.getMessage(), e);
            throw new DocumentUploadException(
                    "Failed to store document file: " + e.getMessage(),
                    "FILE_STORAGE_ERROR",
                    documentTypeName,
                    file.getOriginalFilename(),
                    e
            );
        } catch (Exception e) {
            log.error("Unexpected error during document upload: {}", e.getMessage(), e);
            throw new DocumentUploadException(
                    "Unexpected error during document upload: " + e.getMessage(),
                    "UPLOAD_ERROR",
                    documentTypeName,
                    file.getOriginalFilename(),
                    e
            );
        }
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

    /**
     * Get document image by storage ID and document type.
     *
     * @param storageId The storage ID
     * @param documentType The document type
     * @return The document image as a byte array
     * @throws IOException If an error occurs while reading the file
     * @throws IllegalArgumentException If the document is not found
     */
    public byte[] getDocumentImageByStorageIdAndType(String storageId, String documentType) throws IOException {
        // Construct the storage directory path
        String storageLocation = documentStorageService.getStorageLocation();
        Path storageDir = Paths.get(storageLocation, storageId);

        if (!Files.exists(storageDir)) {
            throw new IllegalArgumentException("Storage directory not found: " + storageDir);
        }

        // Look for files that contain the document type in their name
        try (var stream = Files.walk(storageDir, 1)) {
            Optional<Path> documentFile = stream
                    .filter(path -> !Files.isDirectory(path))
                    .filter(path -> {
                        String fileName = path.getFileName().toString().toLowerCase();
                        String docType = documentType.toLowerCase();
                        return fileName.contains(docType);
                    })
                    .findFirst();

            if (documentFile.isEmpty()) {
                throw new IllegalArgumentException("Document of type '" + documentType + "' not found in storage directory: " + storageDir);
            }

            return Files.readAllBytes(documentFile.get());
        }
    }
}