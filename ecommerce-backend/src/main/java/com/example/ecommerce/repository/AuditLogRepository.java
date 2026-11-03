package com.example.ecommerce.repository;

import com.example.ecommerce.model.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    List<AuditLog> findByEntityTypeAndEntityIdOrderByCreatedAtAsc(String entityType, Long entityId);

    /** Nightly clean-up: old activity entries. Order history entries are kept forever (they are the order timeline). */
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Query("DELETE FROM AuditLog a WHERE a.createdAt < :before AND a.entityType <> 'Order'")
    int deleteOlderThanExceptOrders(@org.springframework.data.repository.query.Param("before") java.time.LocalDateTime before);
}
