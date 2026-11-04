package com.example.ecommerce.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** A phone or computer that allowed notifications. Admin devices get new-order alerts; customers get their own order updates. */
@Entity
@Table(name = "push_devices", uniqueConstraints = @UniqueConstraint(columnNames = "endpoint_hash"))
public class PushDevice {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The browser push service address. Long, so it is looked up by its SHA-256 instead. */
    @Column(nullable = false, length = 1000)
    private String endpoint;

    @Column(name = "endpoint_hash", nullable = false, length = 64)
    private String endpointHash;

    @Column(nullable = false, length = 120)
    private String p256dh;

    @Column(nullable = false, length = 40)
    private String auth;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** True for the shop owner's devices (new paid order alerts). */
    @Column(nullable = false)
    private boolean admin;

    @Column(length = 60)
    private String label;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "last_ok_at")
    private LocalDateTime lastOkAt;

    public Long getId() { return id; }
    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
    public String getEndpointHash() { return endpointHash; }
    public void setEndpointHash(String endpointHash) { this.endpointHash = endpointHash; }
    public String getP256dh() { return p256dh; }
    public void setP256dh(String p256dh) { this.p256dh = p256dh; }
    public String getAuth() { return auth; }
    public void setAuth(String auth) { this.auth = auth; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public boolean isAdmin() { return admin; }
    public void setAdmin(boolean admin) { this.admin = admin; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getLastOkAt() { return lastOkAt; }
    public void setLastOkAt(LocalDateTime lastOkAt) { this.lastOkAt = lastOkAt; }
}
