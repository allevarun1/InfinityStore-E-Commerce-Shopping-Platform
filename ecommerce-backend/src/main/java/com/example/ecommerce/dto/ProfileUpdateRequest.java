package com.example.ecommerce.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public class ProfileUpdateRequest {
    @Size(max = 100, message = "First name can be up to 100 characters")
    private String firstName;
    @Size(max = 100, message = "Last name can be up to 100 characters")
    private String lastName;
    @Size(max = 20, message = "Phone number can be up to 20 characters")
    private String phone;
    @Size(max = 1000, message = "Address can be up to 1000 characters")
    private String addresses;

    @Email
    private String email;

    @Size(min = 8, max = 72, message = "Password must be 8-72 characters")
    private String password;

    /** Required when changing the email address or password. */
    private String currentPassword;

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
    }

    public String getCurrentPassword() { return currentPassword; }

    public void setCurrentPassword(String currentPassword) { this.currentPassword = currentPassword; }
}
