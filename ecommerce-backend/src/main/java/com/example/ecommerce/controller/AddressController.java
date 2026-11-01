package com.example.ecommerce.controller;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import com.example.ecommerce.model.SavedAddress;
import com.example.ecommerce.model.User;
import com.example.ecommerce.repository.SavedAddressRepository;
import com.example.ecommerce.service.BusinessException;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * The customer's address book. Checkout saves the address it delivered to (when the shopper keeps
 * "save this address" ticked) and offers the saved ones first next time, on any device.
 */
@RestController
@RequestMapping("/api/user/addresses")
@PreAuthorize("hasRole('CUSTOMER') and !hasRole('ADMIN')")
public class AddressController extends BaseUserController {
    static final int MAX_ADDRESSES = 10;

    @Autowired
    private SavedAddressRepository addresses;

    public record AddressRequest(
            @NotBlank @Size(min = 2, max = 80) String fullName,
            @NotBlank @Pattern(regexp = "^[6-9]\\d{9}$", message = "Enter a valid 10-digit mobile number") String phone,
            @NotBlank @Size(min = 5, max = 160) String line1,
            @Size(max = 160) String line2,
            @NotBlank @Size(min = 2, max = 60) String city,
            @NotBlank @Size(max = 60) String state,
            @NotBlank @Pattern(regexp = "^[1-9]\\d{5}$", message = "Enter a valid 6-digit PIN code") String pincode) {
    }

    public record AddressView(Long id, String fullName, String phone, String line1, String line2, String city,
                              String state, String pincode) {
        static AddressView of(SavedAddress a) {
            return new AddressView(a.getId(), a.getFullName(), a.getPhone(), a.getLine1(), a.getLine2(), a.getCity(),
                    a.getState(), a.getPincode());
        }
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<AddressView> list() {
        return addresses.findByUserOrderByLastUsedAtDesc(currentUser()).stream().map(AddressView::of).toList();
    }

    /** Saves an address, or refreshes the matching saved one (same mobile, first line and PIN code). */
    @PostMapping
    @Transactional
    public ResponseEntity<AddressView> save(@Valid @RequestBody AddressRequest in) {
        User user = currentUser();
        List<SavedAddress> mine = addresses.findByUserOrderByLastUsedAtDesc(user);
        SavedAddress a = mine.stream().filter(x -> sameAddress(x, in)).findFirst().orElse(null);
        if (a == null) {
            a = new SavedAddress(user);
            // Keep the book short: drop the least recently used beyond the limit.
            for (int i = MAX_ADDRESSES - 1; i < mine.size(); i++) addresses.delete(mine.get(i));
        }
        a.setFullName(in.fullName().trim());
        a.setPhone(in.phone());
        a.setLine1(in.line1().trim());
        a.setLine2(in.line2() == null || in.line2().isBlank() ? null : in.line2().trim());
        a.setCity(in.city().trim());
        a.setState(in.state().trim());
        a.setPincode(in.pincode());
        a.setLastUsedAt(LocalDateTime.now());
        SavedAddress saved = addresses.save(a);
        // Customers who signed in by OTP have no name yet: take it from their first delivery address.
        if (user.getFirstName() == null || user.getFirstName().isBlank()) {
            String[] parts = saved.getFullName().split("\\s+", 2);
            user.setFirstName(parts[0]);
            if (parts.length > 1 && (user.getLastName() == null || user.getLastName().isBlank())) user.setLastName(parts[1]);
        }
        return ResponseEntity.ok(AddressView.of(saved));
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        SavedAddress a = addresses.findByIdAndUser(id, currentUser())
                .orElseThrow(() -> new BusinessException("Address not found"));
        addresses.delete(a);
        return ResponseEntity.noContent().build();
    }

    private static boolean sameAddress(SavedAddress a, AddressRequest in) {
        return a.getPhone().equals(in.phone()) && a.getPincode().equals(in.pincode())
                && a.getLine1().trim().equalsIgnoreCase(in.line1().trim());
    }
}
