package com.example.auth_service.service;

import com.example.auth_service.entity.SecurityAuditLog;
import com.example.auth_service.repository.SecurityAuditLogRepository;
import org.springframework.stereotype.Service;

/**
 * Writes "who did what" into the table security_audit_log
 * (for example LOGIN_SUCCESS, CREATE_USER, DELETE_USER).
 * In production this is how you find out who changed what.
 */
@Service
public class SecurityAuditService {

    private final SecurityAuditLogRepository securityAuditLogRepository;

    public SecurityAuditService(SecurityAuditLogRepository securityAuditLogRepository) {
        this.securityAuditLogRepository = securityAuditLogRepository;
    }

    public void record(String username, String action, String details) {
        String who = (username == null || username.isBlank()) ? "unknown" : username;
        securityAuditLogRepository.save(new SecurityAuditLog(who, action, details));
    }
}
