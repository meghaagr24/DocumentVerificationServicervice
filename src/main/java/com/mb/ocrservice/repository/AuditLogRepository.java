package com.mb.ocrservice.repository;

import com.mb.ocrservice.model.AuditLog;
import com.mb.ocrservice.model.Document;
import com.mb.ocrservice.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Integer> {
    
    List<AuditLog> findByUser(User user);
    
    Page<AuditLog> findByUser(User user, Pageable pageable);
    
    List<AuditLog> findByDocument(Document document);
    
    Page<AuditLog> findByDocument(Document document, Pageable pageable);
    
    List<AuditLog> findByAction(String action);
    
    Page<AuditLog> findByAction(String action, Pageable pageable);
    
    List<AuditLog> findByCreatedAtBetween(ZonedDateTime startDate, ZonedDateTime endDate);
    
    Page<AuditLog> findByCreatedAtBetween(ZonedDateTime startDate, ZonedDateTime endDate, Pageable pageable);
    
    List<AuditLog> findByIpAddress(String ipAddress);
    
    Page<AuditLog> findByIpAddress(String ipAddress, Pageable pageable);
}