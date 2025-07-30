package com.mb.ocrservice.service;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit test for S3StorageService using Mockito.
 * 
 * This test demonstrates how to test the S3StorageService with mocked S3 client
 * to avoid requiring a real Docker environment.
 */
@ExtendWith(MockitoExtension.class)
public class S3StorageServiceTest {

    @Mock
    private AmazonS3 s3Client;

    @InjectMocks
    private S3StorageService storageService;

    private final String bucketName = "test-bucket";

    @BeforeEach
    void setUp() {
        // Set the bucket name using reflection
        ReflectionTestUtils.setField(storageService, "bucketName", bucketName);
    }

    @Test
    void testStoreAndRetrieveDocument() throws IOException {
        // Create a test file
        MultipartFile file = new MockMultipartFile(
                "test.txt",
                "test.txt",
                "text/plain",
                "This is a test file".getBytes(StandardCharsets.UTF_8)
        );

        // Mock S3 client behavior for storing the document
        ArgumentCaptor<PutObjectRequest> putRequestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        
        // Mock S3 client behavior for retrieving the document
        S3Object s3Object = mock(S3Object.class);
        S3ObjectInputStream s3InputStream = mock(S3ObjectInputStream.class);
        when(s3Object.getObjectContent()).thenReturn(s3InputStream);
        when(s3Client.getObject(eq(bucketName), anyString())).thenReturn(s3Object);
        when(s3InputStream.read(any(byte[].class)))
                .thenAnswer(invocation -> {
                    byte[] buffer = invocation.getArgument(0);
                    byte[] content = "This is a test file".getBytes(StandardCharsets.UTF_8);
                    System.arraycopy(content, 0, buffer, 0, content.length);
                    return content.length;
                })
                .thenReturn(-1); // End of stream

        // Store the document
        String key = storageService.storeDocument(file, "TEST");
        
        // Verify the S3 client was called with the correct parameters
        verify(s3Client).putObject(putRequestCaptor.capture());
        PutObjectRequest capturedRequest = putRequestCaptor.getValue();
        assertEquals(bucketName, capturedRequest.getBucketName());
        assertTrue(capturedRequest.getKey().startsWith("test/"));
        assertTrue(capturedRequest.getKey().endsWith(".txt"));
        
        assertNotNull(key);
        assertTrue(key.startsWith("test/"));
        assertTrue(key.endsWith(".txt"));

        // Retrieve the document
        byte[] content = storageService.getDocumentContent(key);
        assertNotNull(content);
        assertEquals("This is a test file", new String(content, StandardCharsets.UTF_8));

        // Mock S3 client behavior for deleting the document
        doNothing().when(s3Client).deleteObject(bucketName, key);
        
        // Delete the document
        storageService.deleteDocument(key);
        verify(s3Client).deleteObject(bucketName, key);
        
        // Mock S3 client behavior for retrieving a deleted document (should throw exception)
        when(s3Client.getObject(eq(bucketName), eq(key))).thenThrow(new AmazonS3Exception("The specified key does not exist."));
        
        // Verify the document was deleted
        assertThrows(IOException.class, () -> storageService.getDocumentContent(key));
    }

    @Test
    void testStoreDocumentWithStorageId() throws IOException {
        // Create a test file
        MultipartFile file = new MockMultipartFile(
                "test.txt",
                "test.txt",
                "text/plain",
                "This is a test file with storage ID".getBytes(StandardCharsets.UTF_8)
        );

        String storageId = "applicant_123";
        String documentType = "PAN";
        String expectedKey = storageId + "/" + documentType + "_" + file.getOriginalFilename();

        // Mock S3 client behavior for listing objects (to check for existing files)
        ListObjectsV2Result listResult = mock(ListObjectsV2Result.class);
        List<S3ObjectSummary> objectSummaries = new ArrayList<>();
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(listResult);
        when(listResult.getObjectSummaries()).thenReturn(objectSummaries);

        // Mock S3 client behavior for storing the document
        ArgumentCaptor<PutObjectRequest> putRequestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        
        // Mock S3 client behavior for retrieving the document
        S3Object s3Object = mock(S3Object.class);
        S3ObjectInputStream s3InputStream = mock(S3ObjectInputStream.class);
        when(s3Object.getObjectContent()).thenReturn(s3InputStream);
        when(s3Client.getObject(eq(bucketName), eq(expectedKey))).thenReturn(s3Object);
        when(s3InputStream.read(any(byte[].class)))
                .thenAnswer(invocation -> {
                    byte[] buffer = invocation.getArgument(0);
                    byte[] content = "This is a test file with storage ID".getBytes(StandardCharsets.UTF_8);
                    System.arraycopy(content, 0, buffer, 0, content.length);
                    return content.length;
                })
                .thenReturn(-1); // End of stream

        // Store the document
        String key = storageService.storeDocument(file, documentType, storageId);
        
        // Verify the S3 client was called with the correct parameters
        verify(s3Client).putObject(putRequestCaptor.capture());
        PutObjectRequest capturedRequest = putRequestCaptor.getValue();
        assertEquals(bucketName, capturedRequest.getBucketName());
        assertEquals(expectedKey, capturedRequest.getKey());
        
        assertNotNull(key);
        assertEquals(expectedKey, key);

        // Retrieve the document
        byte[] content = storageService.getDocumentContent(key);
        assertNotNull(content);
        assertEquals("This is a test file with storage ID", new String(content, StandardCharsets.UTF_8));

        // Create a second file
        MultipartFile file2 = new MockMultipartFile(
                "test2.txt",
                "test2.txt",
                "text/plain",
                "This is a replacement file".getBytes(StandardCharsets.UTF_8)
        );
        
        String expectedKey2 = storageId + "/" + documentType + "_" + file2.getOriginalFilename();
        
        // Mock S3 client behavior for listing objects (to check for existing files)
        // This time, return the first file as an existing object to be deleted
        S3ObjectSummary existingObject = new S3ObjectSummary();
        existingObject.setBucketName(bucketName);
        existingObject.setKey(expectedKey);
        objectSummaries.add(existingObject);
        
        // Mock S3 client behavior for retrieving the second document
        S3Object s3Object2 = mock(S3Object.class);
        S3ObjectInputStream s3InputStream2 = mock(S3ObjectInputStream.class);
        when(s3Object2.getObjectContent()).thenReturn(s3InputStream2);
        when(s3Client.getObject(eq(bucketName), eq(expectedKey2))).thenReturn(s3Object2);
        when(s3InputStream2.read(any(byte[].class)))
                .thenAnswer(invocation -> {
                    byte[] buffer = invocation.getArgument(0);
                    byte[] content2 = "This is a replacement file".getBytes(StandardCharsets.UTF_8);
                    System.arraycopy(content2, 0, buffer, 0, content2.length);
                    return content2.length;
                })
                .thenReturn(-1); // End of stream
        
        // Mock S3 client behavior for the first key (should throw exception after deletion)
        when(s3Client.getObject(eq(bucketName), eq(expectedKey))).thenThrow(new AmazonS3Exception("The specified key does not exist."));

        // Store the second document
        String key2 = storageService.storeDocument(file2, documentType, storageId);
        
        // Verify the first document was deleted
        verify(s3Client).deleteObject(bucketName, expectedKey);
        
        assertNotNull(key2);
        assertEquals(expectedKey2, key2);

        // Verify the first document was deleted
        assertThrows(IOException.class, () -> storageService.getDocumentContent(key));

        // Retrieve the second document
        byte[] content2 = storageService.getDocumentContent(key2);
        assertNotNull(content2);
        assertEquals("This is a replacement file", new String(content2, StandardCharsets.UTF_8));
    }
}
