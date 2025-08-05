package com.mb.ocrservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.vision.v1.*;
import com.google.protobuf.ByteString;
import com.mb.ocrservice.exception.OcrProcessingException;
import com.mb.ocrservice.model.Document;
import com.mb.ocrservice.model.DocumentType;
import com.mb.ocrservice.model.OcrResult;
import com.mb.ocrservice.repository.DocumentRepository;
import com.mb.ocrservice.repository.OcrResultRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class OcrService {

    private final ImageAnnotatorClient imageAnnotatorClient;
    private final DocumentRepository documentRepository;
    private final OcrResultRepository ocrResultRepository;
    private final ObjectMapper objectMapper;
    private final StorageService storageService;

    @Autowired
    public OcrService(
            ImageAnnotatorClient imageAnnotatorClient,
            DocumentRepository documentRepository,
            OcrResultRepository ocrResultRepository,
            ObjectMapper objectMapper,
            StorageService storageService) {
        this.imageAnnotatorClient = imageAnnotatorClient;
        this.documentRepository = documentRepository;
        this.ocrResultRepository = ocrResultRepository;
        this.objectMapper = objectMapper;
        this.storageService = storageService;
    }

    /**
     * Process a document asynchronously using OCR.
     *
     * @param documentId The ID of the document to process
     * @param storageId
     * @return A CompletableFuture that will be completed when the OCR processing is done
     */
    @Async
    public CompletableFuture<OcrResult> processDocumentAsync(Integer documentId, String storageId) {
        return CompletableFuture.supplyAsync(() -> processDocument(documentId, storageId));
    }

    /**
     * Process a document using OCR.
     *
     * @param documentId The ID of the document to process
     * @param storageId
     * @return The OCR result
     */
    @Transactional
    public OcrResult processDocument(Integer documentId, String storageId) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found with ID: " + documentId));

        try {
            // Update document status
            document.setStatus(Document.Status.PROCESSING.name());
            document = documentRepository.save(document);

            // Check if OCR result already exists for this document
            Optional<OcrResult> existingOcrResult = ocrResultRepository.findByDocumentId(documentId);
            OcrResult ocrResult;
            
            if (existingOcrResult.isPresent()) {
                // Use existing OCR result
                log.info("Found existing OCR result for document ID: {}, updating it", documentId);
                ocrResult = existingOcrResult.get();
            } else {
                // Create new OCR result
                log.info("Creating new OCR result for document ID: {}", documentId);
                ocrResult = new OcrResult();
                ocrResult.setDocument(document);
            }

            // Start processing time measurement
            long startTime = System.currentTimeMillis();

            // Read document file using StorageService
            byte[] fileData = storageService.getDocumentByTypeAndStorage(document.getDocumentType().getName(), storageId, document.getFilePath());

            String extractedText;
            float confidenceScore;
            
            // Check if we have a real Google Vision client or if we need to use mock processing
            if (imageAnnotatorClient != null) {
                // Use real Google Vision API
                // Prepare request to Google Vision API
                ByteString imgBytes = ByteString.copyFrom(fileData);
                com.google.cloud.vision.v1.Image image = com.google.cloud.vision.v1.Image.newBuilder()
                        .setContent(imgBytes)
                        .build();

                // Create feature list for the request
                Feature textDetectionFeature = Feature.newBuilder()
                        .setType(Feature.Type.DOCUMENT_TEXT_DETECTION)
                        .build();

                // Create the request
                AnnotateImageRequest request = AnnotateImageRequest.newBuilder()
                        .addFeatures(textDetectionFeature)
                        .setImage(image)
                        .build();

                // Send request to Google Vision API
                BatchAnnotateImagesResponse response = imageAnnotatorClient.batchAnnotateImages(
                        List.of(request));

                // Process response
                AnnotateImageResponse imageResponse = response.getResponses(0);

                if (imageResponse.hasError()) {
                    log.error("Error performing OCR: {}", imageResponse.getError().getMessage());
                    document.setStatus(Document.Status.FAILED.name());
                    documentRepository.save(document);
                    throw new OcrProcessingException("Error performing OCR: " + imageResponse.getError().getMessage());
                }

                // Extract text annotations
                TextAnnotation fullTextAnnotation = imageResponse.getFullTextAnnotation();
                extractedText = fullTextAnnotation.getText();
                
                // Calculate confidence score
                confidenceScore = calculateConfidenceScore(fullTextAnnotation);
            } else {
                // Use mock processing for development
                log.info("Using mock OCR processing for document ID: {}", documentId);
                extractedText = generateMockExtractedText(document.getDocumentType());
                confidenceScore = 0.85f; // Mock confidence score
            }

            // Calculate processing time
            long processingTime = System.currentTimeMillis() - startTime;

            // Extract structured data based on document type
            Map<String, Object> structuredData = extractStructuredData(
                    extractedText,
                    document.getDocumentType());

            // Update OCR result with new data
            ocrResult.setRawText(extractedText);
            ocrResult.setStructuredData(structuredData);
            ocrResult.setConfidenceScore(BigDecimal.valueOf(confidenceScore));
            ocrResult.setProcessingTime((int) processingTime);

            OcrResult savedResult = ocrResultRepository.save(ocrResult);

            // Update document status
            document.setStatus(Document.Status.COMPLETED.name());
            documentRepository.save(document);

            return savedResult;

        } catch (IOException e) {
            log.error("Failed to process document with ID: {}", documentId, e);
            document.setStatus(Document.Status.FAILED.name());
            documentRepository.save(document);
            throw new OcrProcessingException("Failed to process document: " + e.getMessage(), e);
        }
    }

    /**
     * Extract structured data from the OCR text based on document type.
     *
     * @param extractedText The text extracted from the document
     * @param documentType The type of the document
     * @return A map of structured data
     */
    private Map<String, Object> extractStructuredData(String extractedText, DocumentType documentType) {
        String docType = documentType.getName();
        
        switch (docType) {
            case "AADHAAR":
                return extractAadhaarData(extractedText);
            case "PAN":
                return extractPanData(extractedText);
            case "DRIVING_LICENSE":
                return extractDrivingLicenseData(extractedText);
            case "BANK_STATEMENT":
                return extractBankStatementData(extractedText);
            default:
                log.warn("Unknown document type: {}", docType);
                return new HashMap<>();
        }
    }

    /**
     * Extract structured data from Aadhaar card.
     *
     * @param text The text extracted from the document
     * @return A map of structured data
     */
    private Map<String, Object> extractAadhaarData(String text) {
        Map<String, Object> data = new HashMap<>();
        
        // Extract Aadhaar number (12 digits, may be space-separated)
        Pattern aadhaarPattern = Pattern.compile("\\d{4}\\s?\\d{4}\\s?\\d{4}");
        Matcher aadhaarMatcher = aadhaarPattern.matcher(text);
        if (aadhaarMatcher.find()) {
            String aadhaarNumber = aadhaarMatcher.group().replaceAll("\\s", "");
            addFieldWithConfidence(data, "aadhaar_number", aadhaarNumber, 0.9f);
        }
        
        // Extract name (typically after "Name:" or similar)
        Pattern namePattern = Pattern.compile("(?i)(?:name|नाम)[:\\s]+([\\p{L}\\s]+)");
        Matcher nameMatcher = namePattern.matcher(text);
        if (nameMatcher.find()) {
            String name = cleanText(nameMatcher.group(1).trim());
            if (!name.isEmpty()) {
                addFieldWithConfidence(data, "name", name, 0.85f);
            }
        }
        
        // Extract date of birth
        Pattern dobPattern = Pattern.compile("(?i)(?:DOB|Date of Birth|जन्म तिथि)[:\\s]+(\\d{2}/\\d{2}/\\d{4})");
        Matcher dobMatcher = dobPattern.matcher(text);
        if (dobMatcher.find()) {
            String dob = dobMatcher.group(1).trim();
            addFieldWithConfidence(data, "date_of_birth", dob, 0.85f);
        }
        
        // Extract gender
        Pattern genderPattern = Pattern.compile("(?i)(Male|Female)");
        Matcher genderMatcher = genderPattern.matcher(text);
        if (genderMatcher.find()) {
            String gender = genderMatcher.group(1).trim();
            addFieldWithConfidence(data, "gender", gender, 0.9f);
        }
        
        // Extract address (more complex, may span multiple lines)
        Pattern addressPattern = Pattern.compile("(?i)(?:Address|पता)[:\\s]+([\\s\\S]+?)(?:\\n\\n|\\d{6}|$)");
        Matcher addressMatcher = addressPattern.matcher(text);
        if (addressMatcher.find()) {
            String address = cleanText(addressMatcher.group(1).trim().replaceAll("\\n", ", "));
            if (!address.isEmpty()) {
                addFieldWithConfidence(data, "address", address, 0.75f);
            }
        }
        
        return data;
    }

    /**
     * Extract structured data from PAN card.
     *
     * @param text The text extracted from the document
     * @return A map of structured data
     */
    private Map<String, Object> extractPanData(String text) {
        Map<String, Object> data = new HashMap<>();
        
        // Extract PAN number (5 letters, 4 digits, 1 letter)
        Pattern panPattern = Pattern.compile("[A-Z]{5}\\d{4}[A-Z]{1}");
        Matcher panMatcher = panPattern.matcher(text);
        if (panMatcher.find()) {
            String panNumber = panMatcher.group().trim();
            addFieldWithConfidence(data, "pan_number", panNumber, 0.9f);
        }
        
        // Split text into lines for better parsing
        String[] lines = text.split("\\n");
        
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            
            // Extract name
            if (line.contains("नाम") || line.contains("Name")) {
                if (i + 1 < lines.length) {
                    String nextLine = lines[i + 1].trim();
                    // Check if next line contains only English letters and spaces
                    if (nextLine.matches("[A-Z\\s]+")) {
                        String name = cleanText(nextLine);
                        if (!name.isEmpty()) {
                            addFieldWithConfidence(data, "name", name, 0.85f);
                        }
                    }
                }
            }
            
            // Extract father's name
            if (line.contains("पिता का नाम") || line.contains("Father's Name")) {
                if (i + 1 < lines.length) {
                    String nextLine = lines[i + 1].trim();
                    // Check if next line contains only English letters and spaces
                    if (nextLine.matches("[A-Z\\s]+")) {
                        String fathersName = cleanText(nextLine);
                        if (!fathersName.isEmpty()) {
                            addFieldWithConfidence(data, "fathers_name", fathersName, 0.8f);
                        }
                    }
                }
            }
            
            // Extract date of birth
            if (line.contains("जन्म की तारीख") || line.contains("Date of Birth")) {
                if (i + 1 < lines.length) {
                    String nextLine = lines[i + 1].trim();
                    // Check if next line contains date format
                    if (nextLine.matches("\\d{2}/\\d{2}/\\d{4}")) {
                        addFieldWithConfidence(data, "date_of_birth", nextLine, 0.85f);
                    }
                }
            }
        }
        
        return data;
    }

    /**
     * Extract structured data from driving license.
     *
     * @param text The text extracted from the document
     * @return A map of structured data
     */
    private Map<String, Object> extractDrivingLicenseData(String text) {
        Map<String, Object> data = new HashMap<>();
        
        // Extract license number
        Pattern licensePattern = Pattern.compile("(?i)(?:DL No|License No)[.:\\s]+(\\w+\\s?\\w+)");
        Matcher licenseMatcher = licensePattern.matcher(text);
        if (licenseMatcher.find()) {
            String licenseNumber = licenseMatcher.group(1).trim();
            addFieldWithConfidence(data, "license_number", licenseNumber, 0.9f);
        }
        
        // Extract name
        Pattern namePattern = Pattern.compile("(?i)(?:Name|नाम)[:\\s]+([\\p{L}\\s]+)");
        Matcher nameMatcher = namePattern.matcher(text);
        if (nameMatcher.find()) {
            String name = cleanText(nameMatcher.group(1).trim());
            if (!name.isEmpty()) {
                addFieldWithConfidence(data, "name", name, 0.85f);
            }
        }
        
        // Extract date of birth
        Pattern dobPattern = Pattern.compile("(?i)(?:DOB|Date of Birth|जन्म तिथि)[:\\s]+(\\d{2}/\\d{2}/\\d{4})");
        Matcher dobMatcher = dobPattern.matcher(text);
        if (dobMatcher.find()) {
            String dob = dobMatcher.group(1).trim();
            addFieldWithConfidence(data, "date_of_birth", dob, 0.85f);
        }
        
        // Extract address
        Pattern addressPattern = Pattern.compile("(?i)(?:Address|पता)[:\\s]+([\\s\\S]+?)(?:\\n\\n|\\d{6}|$)");
        Matcher addressMatcher = addressPattern.matcher(text);
        if (addressMatcher.find()) {
            String address = cleanText(addressMatcher.group(1).trim().replaceAll("\\n", ", "));
            if (!address.isEmpty()) {
                addFieldWithConfidence(data, "address", address, 0.75f);
            }
        }
        
        // Extract valid from date
        Pattern validFromPattern = Pattern.compile("(?i)(?:Valid From|Issue Date)[:\\s]+(\\d{2}/\\d{2}/\\d{4})");
        Matcher validFromMatcher = validFromPattern.matcher(text);
        if (validFromMatcher.find()) {
            String validFrom = validFromMatcher.group(1).trim();
            addFieldWithConfidence(data, "valid_from", validFrom, 0.85f);
        }
        
        // Extract valid until date
        Pattern validUntilPattern = Pattern.compile("(?i)(?:Valid Until|Valid Till|Expiry Date)[:\\s]+(\\d{2}/\\d{2}/\\d{4})");
        Matcher validUntilMatcher = validUntilPattern.matcher(text);
        if (validUntilMatcher.find()) {
            String validUntil = validUntilMatcher.group(1).trim();
            addFieldWithConfidence(data, "valid_until", validUntil, 0.85f);
        }
        
        return data;
    }

    /**
     * Extract structured data from bank statement.
     *
     * @param text The text extracted from the document
     * @return A map of structured data
     */
    private Map<String, Object> extractBankStatementData(String text) {
        Map<String, Object> data = new HashMap<>();
        
        // Extract account number
        Pattern accountPattern = Pattern.compile("(?i)(?:A/C No|Account No|Account Number)[.:\\s]+(\\d[\\d\\s]+)");
        Matcher accountMatcher = accountPattern.matcher(text);
        if (accountMatcher.find()) {
            String accountNumber = accountMatcher.group(1).trim().replaceAll("\\s", "");
            addFieldWithConfidence(data, "account_number", accountNumber, 0.9f);
        }
        
        // Extract account holder name
        Pattern namePattern = Pattern.compile("(?i)(?:Name|Customer Name|Account Name)[:\\s]+([\\p{L}\\s]+)");
        Matcher nameMatcher = namePattern.matcher(text);
        if (nameMatcher.find()) {
            String name = nameMatcher.group(1).trim();
            addFieldWithConfidence(data, "account_holder_name", name, 0.85f);
        }
        
        // Extract bank name
        Pattern bankPattern = Pattern.compile("(?i)(HDFC|SBI|ICICI|AXIS|KOTAK|PNB|BANK OF BARODA|CANARA|UNION BANK)");
        Matcher bankMatcher = bankPattern.matcher(text);
        if (bankMatcher.find()) {
            String bankName = bankMatcher.group(1).trim();
            addFieldWithConfidence(data, "bank_name", bankName, 0.9f);
        }
        
        // Extract statement period
        Pattern periodPattern = Pattern.compile("(?i)(?:Statement Period|Period)[:\\s]+([\\d/\\s\\-]+to[\\d/\\s\\-]+)");
        Matcher periodMatcher = periodPattern.matcher(text);
        if (periodMatcher.find()) {
            String period = periodMatcher.group(1).trim();
            addFieldWithConfidence(data, "statement_period", period, 0.8f);
        }
        
        // Extract opening balance
        Pattern openingPattern = Pattern.compile("(?i)(?:Opening Balance)[:\\s]+([$₹]?[\\d,.]+)");
        Matcher openingMatcher = openingPattern.matcher(text);
        if (openingMatcher.find()) {
            String openingBalance = openingMatcher.group(1).trim();
            addFieldWithConfidence(data, "opening_balance", openingBalance, 0.8f);
        }
        
        // Extract closing balance
        Pattern closingPattern = Pattern.compile("(?i)(?:Closing Balance)[:\\s]+([$₹]?[\\d,.]+)");
        Matcher closingMatcher = closingPattern.matcher(text);
        if (closingMatcher.find()) {
            String closingBalance = closingMatcher.group(1).trim();
            addFieldWithConfidence(data, "closing_balance", closingBalance, 0.8f);
        }
        
        return data;
    }

    /**
     * Add a field with confidence score to the data map.
     *
     * @param data The data map
     * @param fieldName The name of the field
     * @param fieldValue The value of the field
     * @param confidence The confidence score
     */
    private void addFieldWithConfidence(Map<String, Object> data, String fieldName, String fieldValue, float confidence) {
        Map<String, Object> fieldData = new HashMap<>();
        fieldData.put("value", fieldValue);
        fieldData.put("confidence", confidence);
        data.put(fieldName, fieldData);
    }

    /**
     * Clean text by removing Devanagari characters and keeping only English text.
     *
     * @param text The text to clean
     * @return The cleaned text with only English characters
     */
    private String cleanText(String text) {
        if (text == null || text.trim().isEmpty()) {
            return text;
        }
        
        // Remove Devanagari characters (Unicode range: \u0900-\u097F)
        String cleanedText = text.replaceAll("[\\u0900-\\u097F]", "");
        
        // Remove extra whitespace and newlines
        cleanedText = cleanedText.replaceAll("\\s+", " ").trim();
        
        // Remove any remaining non-English characters except spaces, numbers, and common punctuation
        cleanedText = cleanedText.replaceAll("[^a-zA-Z0-9\\s./-]", "");
        
        return cleanedText.trim();
    }

    /**
     * Calculate the average confidence score from the text annotation.
     *
     * @param textAnnotation The text annotation from Google Vision API
     * @return The average confidence score
     */
    private float calculateConfidenceScore(TextAnnotation textAnnotation) {
        float totalConfidence = 0;
        int blockCount = 0;
        
        for (Page page : textAnnotation.getPagesList()) {
            for (Block block : page.getBlocksList()) {
                totalConfidence += block.getConfidence();
                blockCount++;
            }
        }
        
        return blockCount > 0 ? totalConfidence / blockCount : 0;
    }
    
    /**
     * Generate mock extracted text for development purposes.
     *
     * @param documentType The type of the document
     * @return Mock extracted text
     */
    private String generateMockExtractedText(DocumentType documentType) {
        String docType = documentType.getName();
        
        switch (docType) {
            case "AADHAAR":
                return "Government of India\n" +
                       "Unique Identification Authority of India\n" +
                       "Name: John Doe\n" +
                       "DOB: 01/01/1990\n" +
                       "Gender: MALE\n" +
                       "Address: 123 Main Street, Apartment 4B, Bangalore, Karnataka, 560001\n" +
                       "Aadhaar: 1234 5678 9012";
                       
            case "PAN":
                return "INCOME TAX DEPARTMENT\n" +
                       "GOVT. OF INDIA\n" +
                       "Permanent Account Number\n" +
                       "ABCDE1234F\n" +
                       "Name: John Doe\n" +
                       "Father's Name: James Doe\n" +
                       "Date of Birth: 01/01/1990";
                       
            case "DRIVING_LICENSE":
                return "DRIVING LICENSE\n" +
                       "License No: KA01 20120012345\n" +
                       "Name: John Doe\n" +
                       "DOB: 01/01/1990\n" +
                       "Address: 123 Main Street, Apartment 4B, Bangalore, Karnataka, 560001\n" +
                       "Valid From: 01/01/2020\n" +
                       "Valid Until: 31/12/2030\n" +
                       "Blood Group: O+\n" +
                       "Issuing Authority: RTO Bangalore";
                       
            case "BANK_STATEMENT":
                return "HDFC BANK\n" +
                       "Statement of Account\n" +
                       "Account Name: John Doe\n" +
                       "A/C No: 12345678901234\n" +
                       "Statement Period: 01/01/2023 to 31/01/2023\n" +
                       "Opening Balance: ₹50,000.00\n" +
                       "Closing Balance: ₹65,432.10\n" +
                       "Date       Description                 Debit      Credit     Balance\n" +
                       "01/01/2023 Opening Balance                                  50,000.00\n" +
                       "05/01/2023 Salary                                20,000.00  70,000.00\n" +
                       "10/01/2023 Rent Payment               15,000.00            55,000.00\n" +
                       "15/01/2023 Grocery Shopping            2,500.00            52,500.00\n" +
                       "20/01/2023 Utility Bills               1,500.00            51,000.00\n" +
                       "25/01/2023 Online Purchase             1,200.00            49,800.00\n" +
                       "28/01/2023 Interest Credit                         632.10   50,432.10\n" +
                       "30/01/2023 ATM Withdrawal              5,000.00            45,432.10\n" +
                       "31/01/2023 Deposit                                20,000.00  65,432.10";
                       
            default:
                return "Sample document text for development purposes.\n" +
                       "This is a mock text generated for testing OCR functionality.\n" +
                       "No real data is being processed.";
        }
    }
    
    /**
     * Get the OCR result for a document.
     *
     * @param documentId The ID of the document
     * @return The OCR result
     */
    public Optional<OcrResult> getOcrResult(Integer documentId) {
        return ocrResultRepository.findByDocumentId(documentId);
    }
}
