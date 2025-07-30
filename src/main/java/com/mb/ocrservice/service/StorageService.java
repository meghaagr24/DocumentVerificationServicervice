package com.mb.ocrservice.service;

import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;

/**
 * Interface for document storage operations.
 * This interface abstracts the storage mechanism, allowing for different implementations
 * such as local file system storage or cloud storage (S3).
 */
public interface StorageService {
    
    /**
     * Stores a document file in the configured storage location.
     *
     * @param file The document file to store
     * @param documentType The type of document (used for organizing files)
     * @return The path or key where the file is stored
     * @throws IOException If an error occurs during file storage
     */
    String storeDocument(MultipartFile file, String documentType) throws IOException;
    
    /**
     * Stores a document file with the original filename, replacing if it already exists.
     *
     * @param file The document file to store
     * @param documentType The type of document (used for organizing files)
     * @param storageId The storage ID for organizing files
     * @return The path or key where the file is stored
     * @throws IOException If an error occurs during file storage
     */
    String storeDocument(MultipartFile file, String documentType, String storageId) throws IOException;
    
    /**
     * Deletes a document file from storage.
     *
     * @param path The path or key of the file to delete
     * @throws IOException If an error occurs during file deletion
     */
    void deleteDocument(String path) throws IOException;
    
    /**
     * Gets the content of a document as a byte array.
     *
     * @param path The path or key of the file
     * @return The document content as a byte array
     * @throws IOException If an error occurs while reading the file
     */
    byte[] getDocumentContent(String path) throws IOException;
    
    /**
     * Gets the storage location.
     *
     * @return The storage location
     */
    String getStorageLocation();
}
