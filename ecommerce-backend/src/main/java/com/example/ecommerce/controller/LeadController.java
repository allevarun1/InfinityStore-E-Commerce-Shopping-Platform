package com.example.ecommerce.controller;

import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.example.ecommerce.dto.MessageResponse;
import com.example.ecommerce.service.BusinessException;
import com.example.ecommerce.service.LeadService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;

/** Public opt-in capture (back-in-stock, "contact me") and the owner's lead list. */
@RestController
public class LeadController {
    private static final Set<String> SOURCES = Set.of("back_in_stock", "assistant", "assistant_contact", "contact", "footer", "popup", "checkout", "video_call", "enquiry");

    public record LeadRequest(@Size(max = 80) String name, @Size(max = 120) String email, @Size(max = 20) String phone,
                              @Size(max = 300) String interest, Long productId, @Size(max = 40) String source,
                              @AssertTrue(message = "Please agree to be contacted") boolean consent) {}

    public record LeadUpdate(String status, @Size(max = 2000) String notes) {}

    @Autowired private LeadService leads;

    @PostMapping("/api/store/leads")
    public ResponseEntity<MessageResponse> capture(@Valid @RequestBody LeadRequest r) {
        String source = r.source() != null && SOURCES.contains(r.source()) ? r.source() : "contact";
        if ("back_in_stock".equals(source) && r.productId() == null) throw new BusinessException("Which saree should we tell you about?");
        leads.capture(new LeadService.Capture(r.name(), r.email(), r.phone(), r.interest(), r.productId(), source));
        String msg = "back_in_stock".equals(source) ? "Done! We'll tell you the moment it's back."
                : "Thank you! Our team will get in touch with you soon.";
        return ResponseEntity.ok(new MessageResponse(msg));
    }

    @GetMapping("/api/admin/leads")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<LeadService.LeadView>> list() {
        return ResponseEntity.ok(leads.list());
    }

    @PatchMapping("/api/admin/leads/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<LeadService.LeadView> update(@PathVariable Long id, @Valid @RequestBody LeadUpdate u) {
        return ResponseEntity.ok(leads.update(id, u.status(), u.notes()));
    }

    @GetMapping("/api/admin/leads/export")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> export() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"leads.csv\"")
                .contentType(new MediaType("text", "csv"))
                .body(leads.csv());
    }
}
