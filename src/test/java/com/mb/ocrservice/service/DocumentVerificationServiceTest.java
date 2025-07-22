package com.mb.ocrservice.service;

import com.mb.ocrservice.dto.DocumentVerificationCompletedEvent;
import com.mb.ocrservice.dto.OcrResultDto;
import com.mb.ocrservice.dto.ValidationResultDto;
import com.mb.ocrservice.dto.VerifyDocumentEvent;
import com.mb.ocrservice.model.AuditLog;
import com.mb.ocrservice.model.Document;
import com.mb.ocrservice.model.DocumentType;
import com.mb.ocrservice.model.ValidationResult;
import com.mb.ocrservice.repository.AuditLogRepository;
import com.mb.ocrservice.repository.DocumentRepository;
import com.mb.ocrservice.repository.DocumentTypeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DocumentVerificationServiceTest {

    @TempDir
    Path tempDir;

    @Mock
    private DocumentService documentService;

    @Mock
    private DocumentTypeRepository documentTypeRepository;

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private KafkaTemplate<String, DocumentVerificationCompletedEvent> kafkaTemplate;

    @InjectMocks
    private DocumentVerificationService documentVerificationService;
    
    @Spy
    @InjectMocks
    private DocumentVerificationService documentVerificationServiceSpy;

    @Captor
    private ArgumentCaptor<Document> documentCaptor;

    private Path applicationDir;
    private Map<String, Path> storageDirs;
    private Map<String, Path> panCardFiles;
    private DocumentType panDocumentType;
    private Document savedDocument;
    private ValidationResult validationResult;
    private OcrResultDto ocrResultDto;
    private ValidationResultDto validationResultDto;

    @BeforeEach
    void setUp() throws IOException {
        // Set up test directory structure
        String applicationNumber = "APP123456";
        applicationDir = tempDir.resolve(applicationNumber);
        Files.createDirectories(applicationDir);
        
        // Create storage directories and PAN card files for multiple customers
        storageDirs = new HashMap<>();
        panCardFiles = new HashMap<>();
        
        // Create storage directories and files for two customers
        String storageId1 = "STORAGE123456";
        String customerId1 = "applicant_232R";
        Path storageDir1 = tempDir.resolve(storageId1);
        Files.createDirectories(storageDir1);
        Path panCardFile1 = storageDir1.resolve("pan_card.jpg");
        Files.write(panCardFile1, "test image content for customer 1".getBytes());
        storageDirs.put(customerId1, storageDir1);
        panCardFiles.put(customerId1, panCardFile1);
        
        String storageId2 = "STORAGE789012";
        String customerId2 = "applicant_233R";
        Path storageDir2 = tempDir.resolve(storageId2);
        Files.createDirectories(storageDir2);
        Path panCardFile2 = storageDir2.resolve("pan_card.jpg");
        Files.write(panCardFile2, "test image content for customer 2".getBytes());
        storageDirs.put(customerId2, storageDir2);
        panCardFiles.put(customerId2, panCardFile2);
        
        // Set storage location to temp directory
        ReflectionTestUtils.setField(documentVerificationService, "storageLocation", tempDir.toString());
        
        // Set up document type
        panDocumentType = new DocumentType();
        panDocumentType.setId(1);
        panDocumentType.setName("PAN");
        
        // Set up saved document
        savedDocument = new Document();
        savedDocument.setId(1);
        savedDocument.setDocumentType(panDocumentType);
        savedDocument.setFileName("pan_card.jpg");
        savedDocument.setFilePath(panCardFiles.get("applicant_232R").toString());
        savedDocument.setFileSize(30L); // Length of "test image content for customer 1"
        savedDocument.setMimeType("image/jpeg");
        savedDocument.setStatus(Document.Status.PENDING.name());
        
        // Set up validation result
        validationResult = new ValidationResult();
        validationResult.setId(1);
        validationResult.setDocument(savedDocument);
        validationResult.setIsAuthentic(true);
        validationResult.setIsComplete(true);
        validationResult.setOverallConfidenceScore(BigDecimal.valueOf(0.85));
        
        // Set up OCR result DTO
        ocrResultDto = new OcrResultDto();
        ocrResultDto.setId(1);
        ocrResultDto.setDocumentId(1);
        ocrResultDto.setRawText("ABCDE1234F");
        Map<String, Object> structuredData = new HashMap<>();
        structuredData.put("pan_number", Map.of("value", "ABCDE1234F", "confidence", 0.9));
        ocrResultDto.setStructuredData(structuredData);
        ocrResultDto.setConfidenceScore(BigDecimal.valueOf(0.9));
        
        // Set up validation result DTO
        validationResultDto = new ValidationResultDto();
        validationResultDto.setId(1);
        validationResultDto.setDocumentId(1);
        validationResultDto.setAuthentic(true);
        validationResultDto.setComplete(true);
        validationResultDto.setOverallConfidenceScore(BigDecimal.valueOf(0.85));
        
        // Configure mocks
        when(documentTypeRepository.findByName("PAN")).thenReturn(Optional.of(panDocumentType));
        when(documentRepository.save(any(Document.class))).thenReturn(savedDocument);
        when(documentService.processDocumentAsync(anyInt())).thenReturn(CompletableFuture.completedFuture(validationResult));
        when(documentService.getOcrResult(anyInt())).thenReturn(ocrResultDto);
        when(documentService.getValidationResult(anyInt())).thenReturn(validationResultDto);
    }

    @Test
    void testProcessVerifyDocumentEvent() throws IOException {
        // Create test event with multiple storage IDs
        Map<String, String> applicantStorageIds = new HashMap<>();
        applicantStorageIds.put("STORAGE123456", "applicant_232R");
        applicantStorageIds.put("STORAGE789012", "applicant_233R");
        
        VerifyDocumentEvent event = new VerifyDocumentEvent("REQ123", "APP123456", applicantStorageIds, Instant.now().toString());
        
        // Process the event
        // Use the spy to verify the saveDocumentMetadata method is called
        doReturn(savedDocument).when(documentVerificationServiceSpy).saveDocumentMetadata(any(Document.class), any(VerifyDocumentEvent.class));
        documentVerificationServiceSpy.processVerifyDocumentEvent(event);
        
        // Verify saveDocumentMetadata was called for each storage ID
        verify(documentVerificationServiceSpy, times(2)).saveDocumentMetadata(any(Document.class), eq(event));
        
        // Verify document metadata was saved in the original service
        verify(documentRepository, times(2)).save(documentCaptor.capture());
        Document capturedDocument = documentCaptor.getValue();
        assertEquals(panDocumentType, capturedDocument.getDocumentType());
        assertEquals("pan_card.jpg", capturedDocument.getFileName());
        
        // Verify document was processed synchronously for each storage ID
        verify(documentService, times(2)).processDocumentAsync(anyInt());
        verify(documentService, times(2)).getOcrResult(anyInt());
        verify(documentService, times(2)).getValidationResult(anyInt());
        
        // Verify audit logs were created
        verify(auditLogRepository, atLeast(4)).save(any());
        
        // Verify event was published with results for both customers
        verify(kafkaTemplate).send(anyString(), argThat(completedEvent -> {
            DocumentVerificationCompletedEvent evt = (DocumentVerificationCompletedEvent) completedEvent;
            return evt.getCustomerResults().size() == 2 &&
                   evt.getCustomerResults().containsKey("applicant_232R") &&
                   evt.getCustomerResults().containsKey("applicant_233R");
        }));
    }
}