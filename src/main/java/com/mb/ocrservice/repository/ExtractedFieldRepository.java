package com.mb.ocrservice.repository;

import com.mb.ocrservice.model.ExtractedField;
import com.mb.ocrservice.model.OcrResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface ExtractedFieldRepository extends JpaRepository<ExtractedField, Integer> {
    
    List<ExtractedField> findByOcrResult(OcrResult ocrResult);
    
    List<ExtractedField> findByOcrResultId(Integer ocrResultId);
    
    Optional<ExtractedField> findByOcrResultAndFieldName(OcrResult ocrResult, String fieldName);
    
    Optional<ExtractedField> findByOcrResultIdAndFieldName(Integer ocrResultId, String fieldName);
    
    List<ExtractedField> findByIsValid(Boolean isValid);
    
    List<ExtractedField> findByConfidenceScoreGreaterThanEqual(BigDecimal confidenceScore);
    
    List<ExtractedField> findByConfidenceScoreLessThan(BigDecimal confidenceScore);
}