package com.example.ecommerce.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.ecommerce.model.BundleOffer;
import com.example.ecommerce.repository.BundleOfferRepository;

/** Multi-buy tiers: the owner defines them, pricing applies the best one the basket qualifies for. */
@Service
public class MultiBuyService {
    @Autowired private BundleOfferRepository offers;
    @Autowired private AuditService audit;

    /** The tier with the most items that the basket still qualifies for (items counted by quantity). */
    @Transactional(readOnly = true)
    public Optional<BundleOffer> bestFor(int itemCount) {
        BundleOffer best = null;
        for (BundleOffer o : offers.findByActiveTrueOrderByMinItemsAsc()) {
            if (itemCount >= o.getMinItems()) best = o;
        }
        return Optional.ofNullable(best);
    }

    @Transactional(readOnly = true)
    public List<BundleOffer> active() {
        return offers.findByActiveTrueOrderByMinItemsAsc();
    }

    @Transactional(readOnly = true)
    public List<BundleOffer> all() {
        return offers.findAllByOrderByMinItemsAsc();
    }

    @Transactional
    public BundleOffer create(int minItems, BigDecimal percentOff) {
        if (minItems < 2 || minItems > 20) throw new BusinessException("Choose between 2 and 20 items");
        if (percentOff == null || percentOff.compareTo(new BigDecimal("1")) < 0 || percentOff.compareTo(new BigDecimal("50")) > 0) {
            throw new BusinessException("The discount must be between 1% and 50%");
        }
        if (offers.existsByMinItems(minItems)) throw new BusinessException("There is already an offer for " + minItems + " items");
        BundleOffer saved = offers.save(new BundleOffer(minItems, percentOff));
        audit.record("OFFER_CREATED", "BundleOffer", saved.getId(), saved.label());
        return saved;
    }

    @Transactional
    public BundleOffer setActive(Long id, boolean active) {
        BundleOffer o = offers.findById(id).orElseThrow(() -> new BusinessException("Offer not found"));
        o.setActive(active);
        audit.record("OFFER_ACTIVE_CHANGED", "BundleOffer", id, o.label() + " active=" + active);
        return offers.save(o);
    }

    @Transactional
    public void delete(Long id) {
        BundleOffer o = offers.findById(id).orElseThrow(() -> new BusinessException("Offer not found"));
        offers.delete(o);
        audit.record("OFFER_DELETED", "BundleOffer", id, o.label());
    }
}
