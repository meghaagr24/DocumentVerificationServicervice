package com.mb.ocrservice.repository;

import com.mb.ocrservice.model.Document;
import com.mb.ocrservice.model.ValidationResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface ValidationResultRepository extends JpaRepository<ValidationResult, Integer> {
    
    Optional<ValidationResult> findByDocument(Document document);
    
    Optional<ValidationResult> findByDocumentId(Integer documentId);
    
    List<ValidationResult> findByIsAuthentic(Boolean isAuthentic);
    
    List<ValidationResult> findByIsComplete(Boolean isComplete);
    
    List<ValidationResult> findByIsAuthenticAndIsComplete(Boolean isAuthentic, Boolean isComplete);
    
    List<ValidationResult> findByOverallConfidenceScoreGreaterThanEqual(BigDecimal confidenceScore);
    
    List<ValidationResult> findByOverallConfidenceScoreLessThan(BigDecimal confidenceScore);
}