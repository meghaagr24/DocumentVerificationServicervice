package com.mb.ocrservice.service;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

@Service
@Slf4j
@Primary // Make this the primary implementation when multiple StorageService beans exist
public class S3StorageService implements StorageService {

    private final AmazonS3 s3Client;
    
    @Value("${aws.s3.bucket-name}")
    private String bucketName;
    
    public S3StorageService(AmazonS3 s3Client) {
        this.s3Client = s3Client;
    }
    
    @Override
    public String storeDocument(MultipartFile file, String documentType) throws IOException {
        // Generate a unique key for the S3 object
        String originalFilename = StringUtils.cleanPath(file.getOriginalFilename());
        String fileExtension = getFileExtension(originalFilename);
        String key = documentType.toLowerCase() + "/" + UUID.randomUUID() + "." + fileExtension;
        
        // Upload to S3
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(file.getSize());
        metadata.setContentType(file.getContentType());
        
        try (InputStream inputStream = file.getInputStream()) {
            s3Client.putObject(new PutObjectRequest(bucketName, key, inputStream, metadata));
        }
        
        log.info("Stored document in S3: {}", key);
        return key; // Return the S3 key instead of a file path
    }
    
    @Override
    public String storeDocument(MultipartFile file, String documentType, String storageId) throws IOException {
        // Generate a key for the S3 object
        String originalFilename = StringUtils.cleanPath(file.getOriginalFilename());
        String key = storageId + "/" + documentType + "_" + originalFilename;
        
        // Delete any existing files with the same document type prefix
        ListObjectsV2Request listRequest = new ListObjectsV2Request()
                .withBucketName(bucketName)
                .withPrefix(storageId + "/" + documentType + "_");
        
        ListObjectsV2Result listResult = s3Client.listObjectsV2(listRequest);
        for (S3ObjectSummary objectSummary : listResult.getObjectSummaries()) {
            s3Client.deleteObject(bucketName, objectSummary.getKey());
            log.info("Deleted existing document file: {}", objectSummary.getKey());
        }
        
        // Upload to S3
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(file.getSize());
        metadata.setContentType(file.getContentType());
        
        try (InputStream inputStream = file.getInputStream()) {
            s3Client.putObject(new PutObjectRequest(bucketName, key, inputStream, metadata));
        }
        
        log.info("Stored document in S3: {}", key);
        return key;
    }
    
    @Override
    public void deleteDocument(String key) throws IOException {
        try {
            s3Client.deleteObject(bucketName, key);
            log.info("Deleted document from S3: {}", key);
        } catch (Exception e) {
            log.error("Error deleting document from S3: {}", key, e);
            throw new IOException("Failed to delete document: " + e.getMessage(), e);
        }
    }
    
    @Override
    public byte[] getDocumentContent(String key) throws IOException {
        try {
            S3Object s3Object = s3Client.getObject(bucketName, key);
            S3ObjectInputStream inputStream = s3Object.getObjectContent();
            
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            
            return outputStream.toByteArray();
        } catch (Exception e) {
            log.error("Error retrieving document from S3: {}", key, e);
            throw new IOException("Failed to retrieve document: " + e.getMessage(), e);
        }
    }
    
    @Override
    public String getStorageLocation() {
        return "s3://" + bucketName;
    }
    
    /**
     * Uploads a byte array to S3.
     * This is useful for migration or when you already have the file content as a byte array.
     *
     * @param key The S3 key
     * @param content The content to upload
     * @param contentType The content type
     * @return The S3 key
     */
    public String uploadBytes(String key, byte[] content, String contentType) {
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(content.length);
        metadata.setContentType(contentType);
        
        s3Client.putObject(
                new PutObjectRequest(
                        bucketName,
                        key,
                        new ByteArrayInputStream(content),
                        metadata
                )
        );
        
        log.info("Uploaded content to S3: {}", key);
        return key;
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
