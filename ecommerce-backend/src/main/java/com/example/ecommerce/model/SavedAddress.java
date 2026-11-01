package com.example.ecommerce.model;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

/** A delivery address a customer used at checkout, offered again next time on any device. */
@Entity
@Table(name = "saved_addresses", indexes = @Index(name = "idx_saved_addresses_user", columnList = "user_id"))
public class SavedAddress {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnore
    private User user;

    @Column(name = "full_name", nullable = false, length = 80)
    private String fullName;

    @Column(nullable = false, length = 10)
    private String phone;

    @Column(nullable = false, length = 160)
    private String line1;

    @Column(length = 160)
    private String line2;

    @Column(nullable = false, length = 60)
    private String city;

    @Column(nullable = false, length = 60)
    private String state;

    @Column(nullable = false, length = 6)
    private String pincode;

    @Column(name = "last_used_at", nullable = false)
    private LocalDateTime lastUsedAt;

    public SavedAddress() {}

    public SavedAddress(User user) {
        this.user = user;
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getLine1() { return line1; }
    public void setLine1(String line1) { this.line1 = line1; }
    public String getLine2() { return line2; }
    public void setLine2(String line2) { this.line2 = line2; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public String getPincode() { return pincode; }
    public void setPincode(String pincode) { this.pincode = pincode; }
    public LocalDateTime getLastUsedAt() { return lastUsedAt; }
    public void setLastUsedAt(LocalDateTime lastUsedAt) { this.lastUsedAt = lastUsedAt; }
}
