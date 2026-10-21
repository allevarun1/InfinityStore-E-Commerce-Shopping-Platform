package com.example.ecommerce.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.example.ecommerce.dto.MessageResponse;
import com.example.ecommerce.service.TestimonialService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Featured customer quotes: public list for the storefront, management for the owner. */
@RestController
public class TestimonialController {
    public record TestimonialRequest(@NotBlank @Size(max = 60) String name, @Size(max = 60) String location,
                                     @NotBlank @Size(max = 300) String quote, @Min(1) @Max(5) int rating, Long productId) {}

    @Autowired private TestimonialService testimonials;

    @GetMapping("/api/store/testimonials")
    public ResponseEntity<List<TestimonialService.View>> publicList() {
        return ResponseEntity.ok(testimonials.publicList());
    }

    @GetMapping("/api/admin/testimonials")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<TestimonialService.View>> all() {
        return ResponseEntity.ok(testimonials.all());
    }

    @PostMapping("/api/admin/testimonials")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TestimonialService.View> create(@Valid @RequestBody TestimonialRequest r) {
        return ResponseEntity.ok(testimonials.create(new TestimonialService.Input(r.name(), r.location(), r.quote(), r.rating(), r.productId())));
    }

    @PutMapping("/api/admin/testimonials/{id}/active")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TestimonialService.View> setActive(@PathVariable Long id, @RequestParam boolean value) {
        return ResponseEntity.ok(testimonials.setActive(id, value));
    }

    @DeleteMapping("/api/admin/testimonials/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MessageResponse> delete(@PathVariable Long id) {
        testimonials.delete(id);
        return ResponseEntity.ok(new MessageResponse("Testimonial removed"));
    }
}
