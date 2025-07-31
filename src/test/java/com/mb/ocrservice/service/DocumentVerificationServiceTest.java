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
        
        // Configure mocks with lenient stubbing to avoid UnnecessaryStubbingException
        lenient().when(documentTypeRepository.findByName("PAN")).thenReturn(Optional.of(panDocumentType));
        lenient().when(documentRepository.save(any(Document.class))).thenReturn(savedDocument);
        lenient().when(documentService.processDocumentAsync(anyInt())).thenReturn(CompletableFuture.completedFuture(validationResult));
        lenient().when(documentService.getOcrResult(anyInt())).thenReturn(ocrResultDto);
        lenient().when(documentService.getValidationResult(anyInt())).thenReturn(validationResultDto);
    }

    @Test
    void testProcessVerifyDocumentEvent() throws IOException {
        // Create test event with multiple storage IDs
        Map<String, VerifyDocumentEvent.DocDetailEvent> applicantStorageIds = new HashMap<>();
        applicantStorageIds.put("applicant_232R", new VerifyDocumentEvent.DocDetailEvent("STORAGE123456", "PAN", "ABCDE1234F"));
        applicantStorageIds.put("applicant_233R", new VerifyDocumentEvent.DocDetailEvent("STORAGE789012", "PAN", "HCJPD1913B"));
        
        VerifyDocumentEvent event = new VerifyDocumentEvent("REQ123", "APP123456", applicantStorageIds, Instant.now().toString());
        
        // Process the event
        // Allow the real saveDocumentMetadata method to be called, but still return our savedDocument
        doAnswer(invocation -> {
            // Call the real method for verification purposes
            documentVerificationServiceSpy.saveDocumentMetadata(invocation.getArgument(0), invocation.getArgument(1));
            // But return our predefined savedDocument
            return savedDocument;
        }).when(documentVerificationServiceSpy).saveDocumentMetadata(any(Document.class), any(VerifyDocumentEvent.class));
        
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
    
    @Test
    void testExtractDocumentNumber_PanCard_WithUnderscoreKey() {
        // Create OCR result with pan_number key (underscore format)
        OcrResultDto ocrResult = new OcrResultDto();
        Map<String, Object> structuredData = new HashMap<>();
        Map<String, Object> panNumberData = new HashMap<>();
        panNumberData.put("value", "HCJPD1913B");
        panNumberData.put("confidence", 0.9);
        structuredData.put("pan_number", panNumberData);
        ocrResult.setStructuredData(structuredData);
        
        // Use reflection to call the private method
        String extractedNumber = invokeExtractDocumentNumber(ocrResult, "PAN");
        
        // Verify the correct PAN number is extracted
        assertEquals("HCJPD1913B", extractedNumber);
    }
    
    @Test
    void testExtractDocumentNumber_PanCard_WithCamelCaseKey() {
        // Create OCR result with panNumber key (camelCase format)
        OcrResultDto ocrResult = new OcrResultDto();
        Map<String, Object> structuredData = new HashMap<>();
        Map<String, Object> panNumberData = new HashMap<>();
        panNumberData.put("value", "ABCDE1234F");
        panNumberData.put("confidence", 0.85);
        structuredData.put("panNumber", panNumberData);
        ocrResult.setStructuredData(structuredData);
        
        // Use reflection to call the private method
        String extractedNumber = invokeExtractDocumentNumber(ocrResult, "PANCARD");
        
        // Verify the correct PAN number is extracted
        assertEquals("ABCDE1234F", extractedNumber);
    }
    
    @Test
    void testExtractDocumentNumber_PanCard_PreferUnderscoreOverCamelCase() {
        // Create OCR result with both pan_number and panNumber keys
        OcrResultDto ocrResult = new OcrResultDto();
        Map<String, Object> structuredData = new HashMap<>();
        
        // Add pan_number (should be preferred)
        Map<String, Object> panNumberUnderscoreData = new HashMap<>();
        panNumberUnderscoreData.put("value", "HCJPD1913B");
        panNumberUnderscoreData.put("confidence", 0.9);
        structuredData.put("pan_number", panNumberUnderscoreData);
        
        // Add panNumber (should be fallback)
        Map<String, Object> panNumberCamelData = new HashMap<>();
        panNumberCamelData.put("value", "ABCDE1234F");
        panNumberCamelData.put("confidence", 0.8);
        structuredData.put("panNumber", panNumberCamelData);
        
        ocrResult.setStructuredData(structuredData);
        
        // Use reflection to call the private method
        String extractedNumber = invokeExtractDocumentNumber(ocrResult, "PAN");
        
        // Verify that pan_number is preferred over panNumber
        assertEquals("HCJPD1913B", extractedNumber);
    }
    
    @Test
    void testExtractDocumentNumber_AadhaarCard_WithUnderscoreKey() {
        // Create OCR result with aadhaar_number key
        OcrResultDto ocrResult = new OcrResultDto();
        Map<String, Object> structuredData = new HashMap<>();
        Map<String, Object> aadhaarNumberData = new HashMap<>();
        aadhaarNumberData.put("value", "1234 5678 9012");
        aadhaarNumberData.put("confidence", 0.95);
        structuredData.put("aadhaar_number", aadhaarNumberData);
        ocrResult.setStructuredData(structuredData);
        
        // Use reflection to call the private method
        String extractedNumber = invokeExtractDocumentNumber(ocrResult, "AADHAAR");
        
        // Verify the correct Aadhaar number is extracted
        assertEquals("1234 5678 9012", extractedNumber);
    }
    
    @Test
    void testExtractDocumentNumber_ComplexStructuredData() {
        // Create OCR result similar to your example
        OcrResultDto ocrResult = new OcrResultDto();
        Map<String, Object> structuredData = new HashMap<>();
        
        // Add name
        Map<String, Object> nameData = new HashMap<>();
        nameData.put("value", "TEEKA RAM");
        nameData.put("confidence", 0.85);
        structuredData.put("name", nameData);
        
        // Add pan_number
        Map<String, Object> panNumberData = new HashMap<>();
        panNumberData.put("value", "HCJPD1913B");
        panNumberData.put("confidence", 0.9);
        structuredData.put("pan_number", panNumberData);
        
        // Add fathers_name
        Map<String, Object> fathersNameData = new HashMap<>();
        fathersNameData.put("value", "TEEKA RAM");
        fathersNameData.put("confidence", 0.8);
        structuredData.put("fathers_name", fathersNameData);
        
        // Add date_of_birth
        Map<String, Object> dobData = new HashMap<>();
        dobData.put("value", "01/01/1972");
        dobData.put("confidence", 0.85);
        structuredData.put("date_of_birth", dobData);
        
        ocrResult.setStructuredData(structuredData);
        
        // Use reflection to call the private method
        String extractedNumber = invokeExtractDocumentNumber(ocrResult, "PAN");
        
        // Verify the correct PAN number is extracted from complex data
        assertEquals("HCJPD1913B", extractedNumber);
    }
    
    @Test
    void testExtractDocumentNumber_NullStructuredData() {
        // Create OCR result with null structured data
        OcrResultDto ocrResult = new OcrResultDto();
        ocrResult.setStructuredData(null);
        
        // Use reflection to call the private method
        String extractedNumber = invokeExtractDocumentNumber(ocrResult, "PAN");
        
        // Verify null is returned
        assertNull(extractedNumber);
    }
    
    @Test
    void testExtractDocumentNumber_EmptyStructuredData() {
        // Create OCR result with empty structured data
        OcrResultDto ocrResult = new OcrResultDto();
        ocrResult.setStructuredData(new HashMap<>());
        
        // Use reflection to call the private method
        String extractedNumber = invokeExtractDocumentNumber(ocrResult, "PAN");
        
        // Verify null is returned
        assertNull(extractedNumber);
    }
    
    @Test
    void testExtractDocumentNumber_MissingPanNumberKey() {
        // Create OCR result without pan_number or panNumber keys
        OcrResultDto ocrResult = new OcrResultDto();
        Map<String, Object> structuredData = new HashMap<>();
        Map<String, Object> nameData = new HashMap<>();
        nameData.put("value", "TEEKA RAM");
        nameData.put("confidence", 0.85);
        structuredData.put("name", nameData);
        ocrResult.setStructuredData(structuredData);
        
        // Use reflection to call the private method
        String extractedNumber = invokeExtractDocumentNumber(ocrResult, "PAN");
        
        // Verify null is returned when PAN number key is missing
        assertNull(extractedNumber);
    }
    
    @Test
    void testExtractDocumentNumber_EmptyPanNumberValue() {
        // Create OCR result with empty PAN number value
        OcrResultDto ocrResult = new OcrResultDto();
        Map<String, Object> structuredData = new HashMap<>();
        Map<String, Object> panNumberData = new HashMap<>();
        panNumberData.put("value", "");
        panNumberData.put("confidence", 0.9);
        structuredData.put("pan_number", panNumberData);
        ocrResult.setStructuredData(structuredData);
        
        // Use reflection to call the private method
        String extractedNumber = invokeExtractDocumentNumber(ocrResult, "PAN");
        
        // Verify null is returned for empty value
        assertNull(extractedNumber);
    }
    
    @Test
    void testExtractDocumentNumber_WhitespaceOnlyPanNumberValue() {
        // Create OCR result with whitespace-only PAN number value
        OcrResultDto ocrResult = new OcrResultDto();
        Map<String, Object> structuredData = new HashMap<>();
        Map<String, Object> panNumberData = new HashMap<>();
        panNumberData.put("value", "   ");
        panNumberData.put("confidence", 0.9);
        structuredData.put("pan_number", panNumberData);
        ocrResult.setStructuredData(structuredData);
        
        // Use reflection to call the private method
        String extractedNumber = invokeExtractDocumentNumber(ocrResult, "PAN");
        
        // Verify null is returned for whitespace-only value
        assertNull(extractedNumber);
    }
    
    @Test
    void testExtractDocumentNumber_DirectStringValue() {
        // Create OCR result with direct string value (not nested map)
        OcrResultDto ocrResult = new OcrResultDto();
        Map<String, Object> structuredData = new HashMap<>();
        structuredData.put("pan_number", "HCJPD1913B");
        ocrResult.setStructuredData(structuredData);
        
        // Use reflection to call the private method
        String extractedNumber = invokeExtractDocumentNumber(ocrResult, "PAN");
        
        // Verify the direct string value is extracted
        assertEquals("HCJPD1913B", extractedNumber);
    }
    
    /**
     * Helper method to invoke the private extractDocumentNumber method using reflection
     */
    private String invokeExtractDocumentNumber(OcrResultDto ocrResult, String documentType) {
        try {
            java.lang.reflect.Method method = DocumentVerificationService.class.getDeclaredMethod(
                "extractDocumentNumber", OcrResultDto.class, String.class);
            method.setAccessible(true);
            return (String) method.invoke(documentVerificationService, ocrResult, documentType);
        } catch (Exception e) {
            throw new RuntimeException("Failed to invoke extractDocumentNumber method", e);
        }
    }
}
