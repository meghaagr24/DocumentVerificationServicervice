package com.mb.ocrservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mb.ocrservice.model.Document;
import com.mb.ocrservice.model.DocumentType;
import com.mb.ocrservice.model.OcrResult;
import com.mb.ocrservice.model.ValidationResult;
import com.mb.ocrservice.repository.DocumentRepository;
import com.mb.ocrservice.repository.OcrResultRepository;
import com.mb.ocrservice.repository.ValidationResultRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

@Service
@Slf4j
public class ValidationService {

    private final DocumentRepository documentRepository;
    private final OcrResultRepository ocrResultRepository;
    private final ValidationResultRepository validationResultRepository;
    private final ObjectMapper objectMapper;
    
    // Validation patterns
    private static final Map<String, Map<String, Pattern>> VALIDATION_PATTERNS = new HashMap<>();
    
    static {
        // Aadhaar validation patterns
        Map<String, Pattern> aadhaarPatterns = new HashMap<>();
        aadhaarPatterns.put("aadhaar_number", Pattern.compile("^[0-9]{12}$"));
        aadhaarPatterns.put("date_of_birth", Pattern.compile("^(0[1-9]|[12][0-9]|3[01])/(0[1-9]|1[0-2])/[0-9]{4}$"));
        VALIDATION_PATTERNS.put("AADHAAR", aadhaarPatterns);
        
        // PAN validation patterns
        Map<String, Pattern> panPatterns = new HashMap<>();
        panPatterns.put("pan_number", Pattern.compile("^[A-Z]{5}[0-9]{4}[A-Z]{1}$"));
        panPatterns.put("date_of_birth", Pattern.compile("^(0[1-9]|[12][0-9]|3[01])/(0[1-9]|1[0-2])/[0-9]{4}$"));
        VALIDATION_PATTERNS.put("PAN", panPatterns);
        
        // Driving License validation patterns
        Map<String, Pattern> dlPatterns = new HashMap<>();
        dlPatterns.put("date_of_birth", Pattern.compile("^(0[1-9]|[12][0-9]|3[01])/(0[1-9]|1[0-2])/[0-9]{4}$"));
        dlPatterns.put("valid_from", Pattern.compile("^(0[1-9]|[12][0-9]|3[01])/(0[1-9]|1[0-2])/[0-9]{4}$"));
        dlPatterns.put("valid_until", Pattern.compile("^(0[1-9]|[12][0-9]|3[01])/(0[1-9]|1[0-2])/[0-9]{4}$"));
        VALIDATION_PATTERNS.put("DRIVING_LICENSE", dlPatterns);
        
        // Bank Statement validation patterns
        Map<String, Pattern> bankPatterns = new HashMap<>();
        bankPatterns.put("account_number", Pattern.compile("^[0-9]{9,18}$"));
        VALIDATION_PATTERNS.put("BANK_STATEMENT", bankPatterns);
    }
    
    // Required fields for each document type
    private static final Map<String, Set<String>> REQUIRED_FIELDS = new HashMap<>();
    
    static {
        // Aadhaar required fields
        REQUIRED_FIELDS.put("AADHAAR", new HashSet<>(Arrays.asList(
                "aadhaar_number", "name", "date_of_birth", "gender", "address"
        )));
        
        // PAN required fields
        REQUIRED_FIELDS.put("PAN", new HashSet<>(Arrays.asList(
                "pan_number", "name", "fathers_name", "date_of_birth"
        )));
        
        // Driving License required fields
        REQUIRED_FIELDS.put("DRIVING_LICENSE", new HashSet<>(Arrays.asList(
                "license_number", "name", "date_of_birth", "address", "valid_from", "valid_until"
        )));
        
        // Bank Statement required fields
        REQUIRED_FIELDS.put("BANK_STATEMENT", new HashSet<>(Arrays.asList(
                "account_number", "account_holder_name", "bank_name", "statement_period", 
                "opening_balance", "closing_balance"
        )));
    }

    @Autowired
    public ValidationService(
            DocumentRepository documentRepository,
            OcrResultRepository ocrResultRepository,
            ValidationResultRepository validationResultRepository,
            ObjectMapper objectMapper) {
        this.documentRepository = documentRepository;
        this.ocrResultRepository = ocrResultRepository;
        this.validationResultRepository = validationResultRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Validate a document asynchronously.
     *
     * @param documentId The ID of the document to validate
     * @return A CompletableFuture that will be completed when the validation is done
     */
    @Async
    public CompletableFuture<ValidationResult> validateDocumentAsync(Integer documentId) {
        return CompletableFuture.supplyAsync(() -> validateDocument(documentId));
    }

    /**
     * Validate a document.
     *
     * @param documentId The ID of the document to validate
     * @return The validation result
     */
    @Transactional
    public ValidationResult validateDocument(Integer documentId) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found with ID: " + documentId));
        
        OcrResult ocrResult = ocrResultRepository.findByDocumentId(documentId)
                .orElseThrow(() -> new IllegalArgumentException("OCR result not found for document ID: " + documentId));
        
        // Check if validation result already exists for this document
        Optional<ValidationResult> existingValidationResult = validationResultRepository.findByDocumentId(documentId);
        ValidationResult validationResult;
        
        if (existingValidationResult.isPresent()) {
            // Use existing validation result
            log.info("Found existing validation result for document ID: {}, updating it", documentId);
            validationResult = existingValidationResult.get();
        } else {
            // Create new validation result
            log.info("Creating new validation result for document ID: {}", documentId);
            validationResult = new ValidationResult();
            validationResult.setDocument(document);
        }
        
        String documentType = document.getDocumentType().getName();
        Map<String, Object> structuredData = ocrResult.getStructuredData();
        
        // Convert to JsonNode for easier processing
        JsonNode dataNode = objectMapper.valueToTree(structuredData);
        
        // Validate completeness
        boolean isComplete = validateCompleteness(documentType, dataNode);
        
        // Validate format
        Map<String, Boolean> formatValidations = validateFormat(documentType, dataNode);
        boolean isFormatValid = formatValidations.values().stream().allMatch(Boolean::booleanValue);
        
        // Validate confidence scores
        boolean isConfidenceAcceptable = validateConfidence(dataNode);
        
        // Determine authenticity
        boolean isAuthentic = isFormatValid && isConfidenceAcceptable;
        
        // Calculate overall confidence score
        BigDecimal overallConfidenceScore = calculateOverallConfidence(dataNode, formatValidations);
        
        // Create validation details
        ObjectNode validationDetails = createValidationDetails(documentType, dataNode, formatValidations);
        
        // Update validation result with new data
        validationResult.setIsAuthentic(isAuthentic);
        validationResult.setIsComplete(isComplete);
        validationResult.setOverallConfidenceScore(overallConfidenceScore);
        validationResult.setValidationDetails(objectMapper.convertValue(validationDetails, Map.class));
        
        return validationResultRepository.save(validationResult);
    }

    /**
     * Validate the completeness of a document.
     *
     * @param documentType The type of the document
     * @param data The extracted data
     * @return True if the document is complete, false otherwise
     */
    private boolean validateCompleteness(String documentType, JsonNode data) {
        Set<String> requiredFields = REQUIRED_FIELDS.getOrDefault(documentType, Collections.emptySet());
        
        // Check if all required fields are present and have values
        for (String field : requiredFields) {
            JsonNode fieldNode = data.path(field);
            if (fieldNode.isMissingNode() || 
                fieldNode.path("value").isMissingNode() || 
                fieldNode.path("value").asText().isEmpty()) {
                return false;
            }
        }
        
        return true;
    }

    /**
     * Validate the format of fields in a document.
     *
     * @param documentType The type of the document
     * @param data The extracted data
     * @return A map of field names to validation results
     */
    private Map<String, Boolean> validateFormat(String documentType, JsonNode data) {
        Map<String, Boolean> validations = new HashMap<>();
        Map<String, Pattern> patterns = VALIDATION_PATTERNS.getOrDefault(documentType, Collections.emptyMap());
        
        // Validate each field against its pattern
        for (Map.Entry<String, Pattern> entry : patterns.entrySet()) {
            String fieldName = entry.getKey();
            Pattern pattern = entry.getValue();
            
            JsonNode fieldNode = data.path(fieldName);
            if (!fieldNode.isMissingNode() && !fieldNode.path("value").isMissingNode()) {
                String value = fieldNode.path("value").asText();
                boolean isValid = pattern.matcher(value).matches();
                validations.put(fieldName, isValid);
            } else {
                validations.put(fieldName, false);
            }
        }
        
        return validations;
    }

    /**
     * Validate the confidence scores of fields in a document.
     *
     * @param data The extracted data
     * @return True if all confidence scores are acceptable, false otherwise
     */
    private boolean validateConfidence(JsonNode data) {
        final float CONFIDENCE_THRESHOLD = 0.7f;
        
        // Check if all fields have acceptable confidence scores
        Iterator<Map.Entry<String, JsonNode>> fields = data.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            JsonNode fieldNode = field.getValue();
            
            if (!fieldNode.path("confidence").isMissingNode()) {
                float confidence = fieldNode.path("confidence").floatValue();
                if (confidence < CONFIDENCE_THRESHOLD) {
                    return false;
                }
            }
        }
        
        return true;
    }

    /**
     * Calculate the overall confidence score for a document.
     *
     * @param data The extracted data
     * @param formatValidations The format validation results
     * @return The overall confidence score
     */
    private BigDecimal calculateOverallConfidence(JsonNode data, Map<String, Boolean> formatValidations) {
        // Weights for different factors
        final float FIELD_CONFIDENCE_WEIGHT = 0.6f;
        final float FORMAT_VALIDATION_WEIGHT = 0.4f;
        
        // Calculate field confidence
        float totalConfidence = 0;
        int fieldCount = 0;
        
        Iterator<Map.Entry<String, JsonNode>> fields = data.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            JsonNode fieldNode = field.getValue();
            
            if (!fieldNode.path("confidence").isMissingNode()) {
                totalConfidence += fieldNode.path("confidence").floatValue();
                fieldCount++;
            }
        }
        
        float fieldConfidence = fieldCount > 0 ? totalConfidence / fieldCount : 0;
        
        // Calculate format validation confidence
        int validCount = 0;
        for (boolean isValid : formatValidations.values()) {
            if (isValid) {
                validCount++;
            }
        }
        
        float formatConfidence = formatValidations.size() > 0 ? 
                (float) validCount / formatValidations.size() : 0;
        
        // Calculate weighted average
        float overallConfidence = FIELD_CONFIDENCE_WEIGHT * fieldConfidence + 
                FORMAT_VALIDATION_WEIGHT * formatConfidence;
        
        return BigDecimal.valueOf(overallConfidence);
    }

    /**
     * Create validation details for a document.
     *
     * @param documentType The type of the document
     * @param data The extracted data
     * @param formatValidations The format validation results
     * @return The validation details as a JsonNode
     */
    private ObjectNode createValidationDetails(String documentType, JsonNode data, Map<String, Boolean> formatValidations) {
        ObjectNode details = objectMapper.createObjectNode();
        
        // Add document type
        details.put("document_type", documentType);
        
        // Add field validations
        ObjectNode fieldValidations = details.putObject("field_validations");
        
        Iterator<Map.Entry<String, JsonNode>> fields = data.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            String fieldName = field.getKey();
            JsonNode fieldNode = field.getValue();
            
            ObjectNode fieldValidation = fieldValidations.putObject(fieldName);
            
            // Add value
            if (!fieldNode.path("value").isMissingNode()) {
                fieldValidation.put("value", fieldNode.path("value").asText());
            }
            
            // Add confidence
            if (!fieldNode.path("confidence").isMissingNode()) {
                fieldValidation.put("confidence", fieldNode.path("confidence").floatValue());
            }
            
            // Add format validation
            boolean isFormatValid = formatValidations.getOrDefault(fieldName, true);
            fieldValidation.put("format_valid", isFormatValid);
            
            // Add required field validation
            boolean isRequired = REQUIRED_FIELDS.getOrDefault(documentType, Collections.emptySet())
                    .contains(fieldName);
            fieldValidation.put("required", isRequired);
            
            // Add overall field validity
            boolean isFieldValid = (!isRequired || 
                    (!fieldNode.path("value").isMissingNode() && !fieldNode.path("value").asText().isEmpty())) && 
                    isFormatValid;
            fieldValidation.put("valid", isFieldValid);
        }
        
        return details;
    }
    
    /**
     * Get the validation result for a document.
     *
     * @param documentId The ID of the document
     * @return The validation result
     */
    public Optional<ValidationResult> getValidationResult(Integer documentId) {
        return validationResultRepository.findByDocumentId(documentId);
    }
}