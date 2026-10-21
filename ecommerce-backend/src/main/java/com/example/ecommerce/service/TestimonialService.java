package com.example.ecommerce.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.ecommerce.model.Testimonial;
import com.example.ecommerce.repository.ProductRepository;
import com.example.ecommerce.repository.TestimonialRepository;

/** Customer quotes the owner features on the storefront. */
@Service
public class TestimonialService {
    @Autowired private TestimonialRepository repo;
    @Autowired private ProductRepository products;
    @Autowired private AuditService audit;

    public record Input(String name, String location, String quote, int rating, Long productId) {}

    public record View(Long id, String name, String location, String quote, int rating, Long productId, String productName,
                       boolean sample, boolean active) {}

    @Transactional(readOnly = true)
    public List<View> publicList() {
        return repo.findByActiveTrueOrderBySortOrderAscIdAsc().stream().map(this::view).toList();
    }

    @Transactional(readOnly = true)
    public List<View> all() {
        return repo.findAllByOrderBySortOrderAscIdAsc().stream().map(this::view).toList();
    }

    @Transactional
    public View create(Input in) {
        String name = in.name() == null ? "" : in.name().trim();
        String quote = in.quote() == null ? "" : in.quote().trim();
        if (name.isEmpty() || name.length() > 60) throw new BusinessException("Add the customer's first name (up to 60 characters)");
        if (quote.length() < 20 || quote.length() > 300) throw new BusinessException("The quote should be between 20 and 300 characters");
        if (in.rating() < 1 || in.rating() > 5) throw new BusinessException("The rating must be between 1 and 5");
        String location = in.location() == null || in.location().isBlank() ? null : in.location().trim();
        if (location != null && location.length() > 60) throw new BusinessException("Keep the city short");
        if (in.productId() != null && !products.existsById(in.productId())) throw new BusinessException("That saree does not exist");
        Testimonial t = new Testimonial();
        t.setName(name);
        t.setLocation(location);
        t.setQuote(quote);
        t.setRating(in.rating());
        t.setProductId(in.productId());
        t.setSortOrder((int) repo.count() + 1);
        Testimonial saved = repo.save(t);
        audit.record("TESTIMONIAL_ADDED", "Testimonial", saved.getId(), saved.getName());
        return view(saved);
    }

    @Transactional
    public View setActive(Long id, boolean active) {
        Testimonial t = repo.findById(id).orElseThrow(() -> new BusinessException("Testimonial not found"));
        t.setActive(active);
        audit.record("TESTIMONIAL_ACTIVE_CHANGED", "Testimonial", id, t.getName() + " active=" + active);
        return view(repo.save(t));
    }

    @Transactional
    public void delete(Long id) {
        Testimonial t = repo.findById(id).orElseThrow(() -> new BusinessException("Testimonial not found"));
        repo.delete(t);
        audit.record("TESTIMONIAL_DELETED", "Testimonial", id, t.getName());
    }

    private View view(Testimonial t) {
        String productName = null;
        Long productId = null;
        if (t.getProductId() != null) {
            var p = products.findById(t.getProductId());
            if (p.isPresent() && p.get().isPublished()) { productId = p.get().getId(); productName = p.get().getName(); }
        }
        return new View(t.getId(), t.getName(), t.getLocation(), t.getQuote(), t.getRating(), productId, productName, t.isSample(), t.isActive());
    }
}
