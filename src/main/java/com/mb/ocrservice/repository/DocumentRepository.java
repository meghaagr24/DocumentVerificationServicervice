package com.mb.ocrservice.repository;

import com.mb.ocrservice.model.Document;
import com.mb.ocrservice.model.DocumentType;
import com.mb.ocrservice.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentRepository extends JpaRepository<Document, Integer> {
    
    List<Document> findByUser(User user);
    
    Page<Document> findByUser(User user, Pageable pageable);
    
    List<Document> findByDocumentType(DocumentType documentType);
    
    Page<Document> findByDocumentType(DocumentType documentType, Pageable pageable);
    
    List<Document> findByUserAndDocumentType(User user, DocumentType documentType);
    
    Page<Document> findByUserAndDocumentType(User user, DocumentType documentType, Pageable pageable);
    
    List<Document> findByStatus(String status);
    
    Page<Document> findByStatus(String status, Pageable pageable);
    
    Optional<Document> findByFilePathAndFileName(String filePath, String fileName);
}