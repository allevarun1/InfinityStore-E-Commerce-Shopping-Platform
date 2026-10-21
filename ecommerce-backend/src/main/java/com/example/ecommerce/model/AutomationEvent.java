package com.example.ecommerce.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * One thing Autopilot did or prepared: an email it sent, or a draft waiting for the owner. The subject key
 * (for example "cart:user:12") stops the same reminder going to the same person twice.
 */
@Entity
@Table(name = "automation_events", indexes = @Index(name = "idx_automation_subject", columnList = "rule_key, subject_key"))
public class AutomationEvent {
    /** SENT: handed to the mail server. DRAFT: waiting for the owner. NO_MAIL: email is not configured. DISMISSED: owner skipped it. */
    public static final String SENT = "SENT", DRAFT = "DRAFT", NO_MAIL = "NO_MAIL", DISMISSED = "DISMISSED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rule_key", nullable = false, length = 40)
    private String ruleKey;

    @Column(name = "subject_key", nullable = false, length = 120)
    private String subjectKey;

    @Column(length = 120)
    private String recipient;

    @Column(name = "recipient_name", length = 80)
    private String recipientName;

    @Column(length = 20)
    private String phone;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "email_subject", length = 200)
    private String emailSubject;

    @Column(length = 4000)
    private String body;

    @Column(nullable = false, length = 12)
    private String status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "handled_at")
    private LocalDateTime handledAt;

    public AutomationEvent() {}

    public AutomationEvent(String ruleKey, String subjectKey, String title) {
        this.ruleKey = ruleKey;
        this.subjectKey = subjectKey;
        this.title = title;
    }

    public Long getId() { return id; }
    public String getRuleKey() { return ruleKey; }
    public String getSubjectKey() { return subjectKey; }
    public String getRecipient() { return recipient; }
    public void setRecipient(String recipient) { this.recipient = recipient; }
    public String getRecipientName() { return recipientName; }
    public void setRecipientName(String recipientName) { this.recipientName = recipientName; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getTitle() { return title; }
    public String getEmailSubject() { return emailSubject; }
    public void setEmailSubject(String emailSubject) { this.emailSubject = emailSubject; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getHandledAt() { return handledAt; }
    public void setHandledAt(LocalDateTime handledAt) { this.handledAt = handledAt; }
}
