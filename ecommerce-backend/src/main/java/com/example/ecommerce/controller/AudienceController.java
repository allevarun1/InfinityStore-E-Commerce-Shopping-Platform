package com.example.ecommerce.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.ecommerce.service.AudienceService;

/** The owner's view of people: who to tell about a saree, and the customer list with what each is worth. */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AudienceController {
    @Autowired private AudienceService audience;

    @GetMapping("/share/audience")
    public ResponseEntity<List<AudienceService.Person>> audience(@RequestParam Long productId) {
        return ResponseEntity.ok(audience.audienceFor(productId));
    }

    @GetMapping("/customers")
    public ResponseEntity<List<AudienceService.Customer>> customers() {
        return ResponseEntity.ok(audience.customers());
    }
}
