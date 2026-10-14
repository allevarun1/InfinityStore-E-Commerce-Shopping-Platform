package com.example.ecommerce.dto;

import com.example.ecommerce.model.User;

import java.util.List;

/** Account details safe to return to the account owner or an administrator. */
public record UserResponse(
        Long id,
        String username,
        String email,
        String firstName,
        String lastName,
        String phone,
        String addresses,
        List<String> roles) {

    public static UserResponse from(User u) {
        List<String> roles = u.getRoles().stream().map(r -> r.getName().name()).sorted().toList();
        return new UserResponse(u.getId(), u.getUsername(), u.getEmail(), u.getFirstName(), u.getLastName(),
                u.getPhone(), u.getAddresses(), roles);
    }
}
