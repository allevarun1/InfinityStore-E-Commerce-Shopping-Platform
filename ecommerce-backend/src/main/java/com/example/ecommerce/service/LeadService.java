package com.example.ecommerce.service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.ecommerce.model.Lead;
import com.example.ecommerce.model.Product;
import com.example.ecommerce.model.StockRequest;
import com.example.ecommerce.repository.LeadRepository;
import com.example.ecommerce.repository.ProductRepository;
import com.example.ecommerce.repository.StockRequestRepository;

/**
 * Lead capture and the owner's follow-up list. Every lead opted in. Scores are simple, visible rules
 * (what they asked for, how they can be reached, how recently) so the owner can see why someone is "hot".
 */
@Service
public class LeadService {
    public static final Set<String> STATUSES = Set.of(Lead.NEW, Lead.CONTACTED, Lead.WON, Lead.LOST);
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    public record Capture(String name, String email, String phone, String interest, Long productId, String source) {}

    public record LeadView(Long id, String name, String email, String phone, String source, String interest,
                           Long productId, String productName, String status, String notes, int touches,
                           LocalDateTime createdAt, LocalDateTime lastActivityAt, long minutesSinceActivity,
                           int score, String temperature, List<String> reasons, String followUp, String whatsappLink) {}

    @Autowired private LeadRepository leads;
    @Autowired private StockRequestRepository stockRequests;
    @Autowired private ProductRepository products;
    @Autowired private AuditService audit;

    @Value("${app.store.name:Rekha Shopping Mall}")
    private String storeName;

    /**
     * Records an opt-in. One lead per person: a repeat email or phone updates the existing record, adds the
     * new interest to the top and counts another touch. Returns the saved lead.
     */
    @Transactional
    public Lead capture(Capture c) {
        String email = normaliseEmail(c.email());
        String phone = normalisePhone(c.phone());
        if (email == null && phone == null) throw new BusinessException("Please enter an email address or a mobile number.");
        if (c.email() != null && !c.email().isBlank() && email == null) throw new BusinessException("Please enter a valid email address.");
        if (c.phone() != null && !c.phone().isBlank() && phone == null) throw new BusinessException("Please enter a valid 10-digit mobile number.");

        Product product = c.productId() == null ? null : products.findPublicById(c.productId())
                .orElseThrow(() -> new BusinessException("That saree is no longer available."));

        Lead lead = (email != null ? leads.findFirstByEmailIgnoreCase(email) : java.util.Optional.<Lead>empty())
                .or(() -> phone != null ? leads.findFirstByPhone(phone) : java.util.Optional.empty())
                .orElse(null);
        boolean fresh = lead == null;
        if (fresh) {
            lead = new Lead(email, clip(blankToDefault(c.source(), "website"), 40));
        } else {
            lead.setTouches(lead.getTouches() + 1);
            if (lead.getEmail() == null && email != null && !leads.existsByEmailIgnoreCase(email)) lead.setEmail(email);
            // A new request reopens a closed lead so it shows up in the owner's list again.
            if (Lead.LOST.equals(lead.getStatus())) lead.setStatus(Lead.NEW);
            // The strongest intent wins as the headline source.
            if (rank(c.source()) > rank(lead.getSource())) lead.setSource(clip(c.source(), 40));
        }
        if (lead.getPhone() == null && phone != null) lead.setPhone(phone);
        if (c.name() != null && !c.name().isBlank()) lead.setName(clip(c.name().trim(), 80));

        String interest = c.interest() == null ? null : c.interest().trim();
        if (product != null) {
            lead.setProductId(product.getId());
            lead.setProductName(clip(product.getName(), 200));
            if (interest == null || interest.isBlank()) interest = product.getName();
        }
        if (interest != null && !interest.isBlank()) {
            String old = lead.getInterest();
            String merged = old == null || old.isBlank() ? interest : (old.toLowerCase(Locale.ROOT).contains(interest.toLowerCase(Locale.ROOT)) ? old : interest + " | " + old);
            lead.setInterest(clip(merged, 1000));
        }
        lead.setLastActivityAt(LocalDateTime.now());
        lead = leads.save(lead);

        if (product != null && "back_in_stock".equals(c.source())) {
            Lead saved = lead;
            StockRequest request = stockRequests.findByLeadIdAndProductId(lead.getId(), product.getId())
                    .orElseGet(() -> new StockRequest(saved, product.getId()));
            request.setNotifiedAt(null); // asking again re-arms the alert
            stockRequests.save(request);
        }
        return lead;
    }

    @Transactional(readOnly = true)
    public List<LeadView> list() {
        List<LeadView> out = new ArrayList<>();
        for (Lead l : leads.findAllByOrderByLastActivityAtDesc()) out.add(view(l));
        out.sort((a, b) -> {
            int open = Boolean.compare(isOpen(b.status()), isOpen(a.status()));
            return open != 0 ? open : Integer.compare(b.score(), a.score());
        });
        return out;
    }

    @Transactional
    public LeadView update(Long id, String status, String notes) {
        Lead lead = leads.findById(id).orElseThrow(() -> new BusinessException("Lead not found"));
        if (status != null) {
            String s = status.trim().toUpperCase(Locale.ROOT);
            if (!STATUSES.contains(s)) throw new BusinessException("Unknown lead status " + status);
            if (!s.equals(lead.getStatus())) audit.record("LEAD_STATUS", "Lead", id, lead.getStatus() + " -> " + s);
            lead.setStatus(s);
        }
        if (notes != null) lead.setNotes(clip(notes.trim(), 2000));
        return view(leads.save(lead));
    }

    /** CSV for the owner's own spreadsheet or WhatsApp broadcast tool. */
    @Transactional(readOnly = true)
    public String csv() {
        StringBuilder sb = new StringBuilder("name,email,phone,source,status,score,temperature,interest,created_at\n");
        for (LeadView l : list()) {
            sb.append(cell(l.name())).append(',').append(cell(l.email())).append(',').append(cell(l.phone())).append(',')
              .append(cell(l.source())).append(',').append(l.status()).append(',').append(l.score()).append(',')
              .append(l.temperature()).append(',').append(cell(l.interest())).append(',').append(l.createdAt()).append('\n');
        }
        return sb.toString();
    }

    LeadView view(Lead l) {
        List<String> reasons = new ArrayList<>();
        int score = score(l, LocalDateTime.now(), reasons);
        String temp = !isOpen(l.getStatus()) ? l.getStatus().toLowerCase(Locale.ROOT) : score >= 60 ? "hot" : score >= 35 ? "warm" : "cold";
        String message = followUp(l);
        String wa = l.getPhone() == null ? null
                : "https://wa.me/91" + l.getPhone() + "?text=" + URLEncoder.encode(message, StandardCharsets.UTF_8).replace("+", "%20");
        return new LeadView(l.getId(), l.getName(), l.getEmail(), l.getPhone(), l.getSource(), l.getInterest(),
                l.getProductId(), l.getProductName(), l.getStatus(), l.getNotes(), l.getTouches(),
                l.getCreatedAt(), l.getLastActivityAt(), Duration.between(l.getLastActivityAt(), LocalDateTime.now()).toMinutes(), score, temp, reasons, message, wa);
    }

    /** 0 to 100. Each point has a reason the owner can read. */
    static int score(Lead l, LocalDateTime now, List<String> reasons) {
        int s = 0;
        String source = l.getSource() == null ? "" : l.getSource();
        switch (source) {
            case "video_call" -> { s += 35; reasons.add("booked a video shopping call"); }
            case "enquiry" -> { s += 35; reasons.add("sent a wedding, bulk or custom enquiry"); }
            case "contact", "assistant_contact" -> { s += 30; reasons.add("asked to be contacted"); }
            case "back_in_stock" -> { s += 30; reasons.add("waiting for a sold-out saree"); }
            case "assistant" -> { s += 20; reasons.add("chatted with the shopping assistant"); }
            default -> { s += 10; reasons.add("subscribed for offers"); }
        }
        if (l.getPhone() != null) { s += 20; reasons.add("reachable on WhatsApp"); }
        if (l.getProductId() != null || (l.getInterest() != null && !l.getInterest().isBlank())) { s += 15; reasons.add("told us what they want"); }
        int repeat = Math.min(3, l.getTouches() - 1);
        if (repeat > 0) { s += repeat * 8; reasons.add("came back " + repeat + " more time" + (repeat == 1 ? "" : "s")); }
        long hours = Duration.between(l.getLastActivityAt(), now).toHours();
        if (hours <= 48) { s += 15; reasons.add("active in the last 2 days"); }
        else if (hours <= 24 * 7) { s += 8; reasons.add("active this week"); }
        return Math.min(100, s);
    }

    /** A ready-to-send first message. The owner reviews it in WhatsApp before pressing send. */
    String followUp(Lead l) {
        String hi = "Hi" + (l.getName() == null ? "" : " " + l.getName().split(" ")[0]) + ", this is " + storeName + ".";
        if (l.getProductName() != null && "back_in_stock".equals(l.getSource())) {
            return hi + " You asked us to tell you when \"" + l.getProductName() + "\" is back. Shall I keep one aside for you?";
        }
        if ("video_call".equals(l.getSource())) {
            return hi + " Thanks for booking a video call. I'll show you the sarees live so you can see the drape and colour. Is the time you chose still good for you?";
        }
        if ("enquiry".equals(l.getSource())) {
            return hi + " Thanks for your enquiry. I'd love to help with it. Could you tell me the date you need it by and your budget?";
        }
        if (l.getInterest() != null && !l.getInterest().isBlank()) {
            String first = l.getInterest().split(" \\| ")[0];
            return hi + " You were looking for " + first + ". I have a few options I think you'll like. May I send you photos here?";
        }
        return hi + " Thank you for joining us! Are you shopping for an occasion? I can send you our newest sarees.";
    }

    static boolean isOpen(String status) {
        return Lead.NEW.equals(status) || Lead.CONTACTED.equals(status);
    }

    private static int rank(String source) {
        if (source == null) return 0;
        return switch (source) {
            case "video_call", "enquiry" -> 4;
            case "contact", "assistant_contact", "back_in_stock" -> 3;
            case "assistant" -> 2;
            default -> 1;
        };
    }

    static String normaliseEmail(String email) {
        if (email == null || email.isBlank()) return null;
        String e = email.trim().toLowerCase(Locale.ROOT);
        return e.length() <= 120 && EMAIL.matcher(e).matches() ? e : null;
    }

    /** Indian mobile numbers only, stored as 10 digits (the WhatsApp link adds the 91). */
    static String normalisePhone(String phone) {
        if (phone == null || phone.isBlank()) return null;
        String d = phone.replaceAll("\\D", "");
        if (d.length() == 12 && d.startsWith("91")) d = d.substring(2);
        if (d.length() == 11 && d.startsWith("0")) d = d.substring(1);
        return d.matches("[6-9]\\d{9}") ? d : null;
    }

    private static String cell(String v) {
        if (v == null) return "";
        String safe = v.replace("\"", "\"\"");
        // Stop spreadsheet formula injection from user-typed text.
        if (!safe.isEmpty() && "=+-@".indexOf(safe.charAt(0)) >= 0) safe = "'" + safe;
        return "\"" + safe + "\"";
    }

    private static String clip(String v, int max) {
        return v == null ? null : v.length() > max ? v.substring(0, max) : v;
    }

    private static String blankToDefault(String v, String d) {
        return v == null || v.isBlank() ? d : v.trim();
    }
}
