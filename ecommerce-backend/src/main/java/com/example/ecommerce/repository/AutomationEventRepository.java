package com.example.ecommerce.repository;

import com.example.ecommerce.model.AutomationEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AutomationEventRepository extends JpaRepository<AutomationEvent, Long> {
    boolean existsByRuleKeyAndSubjectKeyAndCreatedAtAfter(String ruleKey, String subjectKey, LocalDateTime after);

    boolean existsByRuleKeyAndSubjectKey(String ruleKey, String subjectKey);

    List<AutomationEvent> findTop100ByOrderByCreatedAtDesc();

    List<AutomationEvent> findByStatusOrderByCreatedAtDesc(String status);

    long countByStatus(String status);

    long countByStatusAndCreatedAtAfter(String status, LocalDateTime after);
}
