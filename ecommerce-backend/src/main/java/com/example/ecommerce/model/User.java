package com.example.ecommerce.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "users",
       uniqueConstraints = {
           @UniqueConstraint(columnNames = "username"),
           @UniqueConstraint(columnNames = "email")
       })
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 20)
    private String username;

    @NotBlank
    @Size(max = 50)
    @Email
    private String email;

    @NotBlank
    @Size(max = 120)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private String password;

    @Column(name = "first_name")
    private String firstName;

    @Column(name = "last_name")
    private String lastName;

    @Size(max = 20)
    private String phone;

    @Column(length = 1000)
    private String addresses;

    // Legacy plaintext column from the first reset implementation. No longer written or read.
    @Column(name = "reset_token")
    @com.fasterxml.jackson.annotation.JsonIgnore
    private String resetToken;

    @Column(name = "reset_token_hash", length = 64)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private String resetTokenHash;

    @Column(name = "reset_token_expires_at")
    @com.fasterxml.jackson.annotation.JsonIgnore
    private java.time.LocalDateTime resetTokenExpiresAt;

    @Column(name = "password_changed_at")
    @com.fasterxml.jackson.annotation.JsonIgnore
    private java.time.LocalDateTime passwordChangedAt;

    @Column(name = "reset_attempts")
    @com.fasterxml.jackson.annotation.JsonIgnore
    private Integer resetAttempts;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "user_roles",
               joinColumns = @JoinColumn(name = "user_id"),
               inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<Role> roles = new HashSet<>();

    public User() {}

    public User(String username, String email, String password, String firstName, String lastName) {
        this.username = username;
        this.email = email;
        this.password = password;
        this.firstName = firstName;
        this.lastName = lastName;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
        // Sessions issued before this moment are revoked (see AuthTokenFilter).
        this.passwordChangedAt = java.time.LocalDateTime.now();
    }

    public java.time.LocalDateTime getPasswordChangedAt() {
        return passwordChangedAt;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public Set<Role> getRoles() {
        return roles;
    }

    public void setRoles(Set<Role> roles) {
        this.roles = roles;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getAddresses() {
        return addresses;
    }

    public void setAddresses(String addresses) {
        this.addresses = addresses;
    }

    public String getResetToken() {
        return resetToken;
    }

    public void setResetToken(String resetToken) {
        this.resetToken = resetToken;
    }

    public String getResetTokenHash() { return resetTokenHash; }
    public void setResetTokenHash(String resetTokenHash) { this.resetTokenHash = resetTokenHash; }
    public java.time.LocalDateTime getResetTokenExpiresAt() { return resetTokenExpiresAt; }
    public void setResetTokenExpiresAt(java.time.LocalDateTime resetTokenExpiresAt) { this.resetTokenExpiresAt = resetTokenExpiresAt; }
    public int getResetAttempts() { return resetAttempts == null ? 0 : resetAttempts; }
    public void setResetAttempts(Integer resetAttempts) { this.resetAttempts = resetAttempts; }
}
