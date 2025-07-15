package com.mb.ocrservice.repository;

import com.mb.ocrservice.model.DocumentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DocumentTypeRepository extends JpaRepository<DocumentType, Integer> {
    
    Optional<DocumentType> findByName(String name);
    
    boolean existsByName(String name);
}