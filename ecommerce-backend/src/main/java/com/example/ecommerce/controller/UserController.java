package com.example.ecommerce.controller;

import com.example.ecommerce.dto.MessageResponse;
import com.example.ecommerce.dto.ProfileUpdateRequest;
import com.example.ecommerce.dto.UserResponse;
import com.example.ecommerce.model.User;
import com.example.ecommerce.service.AccountService;
import com.example.ecommerce.service.BusinessException;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user")
@PreAuthorize("hasRole('CUSTOMER') and !hasRole('ADMIN')")
public class UserController extends BaseUserController {
    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AccountService accountService;

    @GetMapping("/profile")
    @Transactional(readOnly = true)
    public ResponseEntity<UserResponse> profile() {
        return ResponseEntity.ok(UserResponse.from(currentUser()));
    }

    @PutMapping("/profile")
    @Transactional
    public ResponseEntity<UserResponse> updateProfile(@Valid @RequestBody ProfileUpdateRequest request) {
        User user = currentUser();
        boolean emailChanged = request.getEmail() != null && !request.getEmail().isBlank()
                && !request.getEmail().equals(user.getEmail());
        boolean passwordChanged = request.getPassword() != null && !request.getPassword().isBlank();

        // Changing credentials requires proof of the current password.
        if (emailChanged || passwordChanged) {
            if (request.getCurrentPassword() == null
                    || !passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
                throw new BusinessException("Current password is incorrect");
            }
        }
        if (emailChanged && userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException("Email is already in use");
        }

        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setPhone(request.getPhone());
        user.setAddresses(request.getAddresses());
        if (emailChanged) {
            user.setEmail(request.getEmail());
        }
        if (passwordChanged) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }
        return ResponseEntity.ok(UserResponse.from(userRepository.save(user)));
    }

    @DeleteMapping("/profile")
    public ResponseEntity<MessageResponse> deleteAccount() {
        accountService.deleteAccount(currentUser().getId());
        return ResponseEntity.ok(new MessageResponse("Account deleted"));
    }
}
