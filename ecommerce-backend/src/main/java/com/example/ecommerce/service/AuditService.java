package com.example.ecommerce.service;

import com.example.ecommerce.model.AuditLog;
import com.example.ecommerce.repository.AuditLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class AuditService {
    @Autowired
    private AuditLogRepository repository;

    /** Records inside the caller's transaction so the audit entry commits or rolls back with the change. */
    public void record(String action, String entityType, Long entityId, String detail) {
        repository.save(new AuditLog(currentActor(), action, entityType, entityId, detail));
    }

    private String currentActor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName()) ? auth.getName() : "system";
    }
}
