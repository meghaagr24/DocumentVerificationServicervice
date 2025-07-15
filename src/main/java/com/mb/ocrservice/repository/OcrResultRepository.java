package com.mb.ocrservice.repository;

import com.mb.ocrservice.model.Document;
import com.mb.ocrservice.model.OcrResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface OcrResultRepository extends JpaRepository<OcrResult, Integer> {
    
    Optional<OcrResult> findByDocument(Document document);
    
    Optional<OcrResult> findByDocumentId(Integer documentId);
    
    List<OcrResult> findByConfidenceScoreGreaterThanEqual(BigDecimal confidenceScore);
    
    List<OcrResult> findByConfidenceScoreLessThan(BigDecimal confidenceScore);
}