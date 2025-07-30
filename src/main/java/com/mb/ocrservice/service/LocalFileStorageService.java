package com.mb.ocrservice.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
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
@Profile("local-fs") // Only active when local-fs profile is active
public class LocalFileStorageService implements StorageService {

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
    @Override
    public String storeDocument(MultipartFile file, String documentType) throws IOException {
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
     * Stores a document file with the original filename, replacing if it already exists.
     *
     * @param file The document file to store
     * @param documentType The type of document (used for organizing files)
     * @param storageId The storage ID for organizing files
     * @return The path where the file is stored
     * @throws IOException If an error occurs during file storage
     */
    @Override
    public String storeDocument(MultipartFile file, String documentType, String storageId) throws IOException {
        // Create storage directory if it doesn't exist
        Path storagePath = Paths.get(storageLocation, storageId);
        if (!Files.exists(storagePath)) {
            Files.createDirectories(storagePath);
        }

        // Generate a unique filename to avoid collisions
        String originalFilename = StringUtils.cleanPath(file.getOriginalFilename());
        String uniqueFilename = documentType + "_" + originalFilename;
        
        // Check if any file starting with the document type already exists and delete it
        try (var stream = Files.walk(storagePath, 1)) {
            stream.filter(path -> !Files.isDirectory(path))
                  .filter(path -> {
                      String fileName = path.getFileName().toString();
                      return fileName.startsWith(documentType + "_");
                  })
                  .forEach(path -> {
                      try {
                          Files.delete(path);
                          log.info("Deleted existing document file: {}", path);
                      } catch (IOException e) {
                          log.warn("Failed to delete existing file: {}", path, e);
                      }
                  });
        }
        
        // Store the file
        Path destinationPath = storagePath.resolve(uniqueFilename);
        
        try (InputStream inputStream = file.getInputStream()) {
            Files.copy(inputStream, destinationPath, StandardCopyOption.REPLACE_EXISTING);
        }
        
        log.info("Stored document: {} as {}", originalFilename, destinationPath);
        return destinationPath.toString();
    }

    /**
     * Deletes a document file from storage.
     *
     * @param filePath The path of the file to delete
     * @throws IOException If an error occurs during file deletion
     */
    @Override
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
     * Gets the content of a document as a byte array.
     *
     * @param filePath The path of the file
     * @return The document content as a byte array
     * @throws IOException If an error occurs while reading the file
     */
    @Override
    public byte[] getDocumentContent(String filePath) throws IOException {
        Path path = Paths.get(filePath);
        if (!Files.exists(path)) {
            throw new IOException("File not found: " + filePath);
        }
        return Files.readAllBytes(path);
    }

    /**
     * Gets the storage location.
     *
     * @return The storage location
     */
    @Override
    public String getStorageLocation() {
        return storageLocation;
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
