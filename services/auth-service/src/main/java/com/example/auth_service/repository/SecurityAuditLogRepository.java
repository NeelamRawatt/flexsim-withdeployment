package com.example.auth_service.repository;

import com.example.auth_service.entity.SecurityAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Database access for the audit log.
 * It is empty on purpose: save() and findAll() come free from JpaRepository.
 */
public interface SecurityAuditLogRepository extends JpaRepository<SecurityAuditLog, Long> {
}