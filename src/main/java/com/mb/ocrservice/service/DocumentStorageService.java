package com.mb.ocrservice.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
@Slf4j
public class DocumentStorageService {

    @Value("${document.storage.location}")
    private String storageLocation;

    /**
     * Stores a document file in the configured storage location.
     *
     * @param file The document file to store
     * @param documentType The type of document (used for organizing files)
     * @return The path where the file is stored
     * @throws IOException If an error occurs during file storage
     */
    public String storeDocument(MultipartFile file, String documentType) throws IOException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Cannot store empty file");
        }

        // Create storage directory if it doesn't exist
        Path storagePath = Paths.get(storageLocation, documentType.toLowerCase());
        if (!Files.exists(storagePath)) {
            Files.createDirectories(storagePath);
        }

        // Generate a unique filename to avoid collisions
        String originalFilename = StringUtils.cleanPath(file.getOriginalFilename());
        String fileExtension = getFileExtension(originalFilename);
        String uniqueFilename = UUID.randomUUID().toString() + "." + fileExtension;
        
        // Store the file
        Path destinationPath = storagePath.resolve(uniqueFilename);
        try (InputStream inputStream = file.getInputStream()) {
            Files.copy(inputStream, destinationPath, StandardCopyOption.REPLACE_EXISTING);
        }
        
        log.info("Stored document: {} as {}", originalFilename, destinationPath);
        return destinationPath.toString();
    }

    /**
     * Retrieves a document file from storage.
     *
     * @param filePath The path of the file to retrieve
     * @return The file as a byte array
     * @throws IOException If an error occurs during file retrieval
     */
    public byte[] getDocument(String filePath) throws IOException {
        Path path = Paths.get(filePath);
        if (!Files.exists(path)) {
            throw new IOException("File not found: " + filePath);
        }
        
        return Files.readAllBytes(path);
    }

    /**
     * Deletes a document file from storage.
     *
     * @param filePath The path of the file to delete
     * @throws IOException If an error occurs during file deletion
     */
    public void deleteDocument(String filePath) throws IOException {
        Path path = Paths.get(filePath);
        if (Files.exists(path)) {
            Files.delete(path);
            log.info("Deleted document: {}", filePath);
        } else {
            log.warn("Document not found for deletion: {}", filePath);
        }
    }

    /**
     * Extracts the file extension from a filename.
     *
     * @param filename The filename
     * @return The file extension
     */
    private String getFileExtension(String filename) {
        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex > 0) {
            return filename.substring(lastDotIndex + 1);
        }
        return "bin"; // Default extension if none is found
    }
}