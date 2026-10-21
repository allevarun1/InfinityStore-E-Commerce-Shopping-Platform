package com.example.ecommerce.service;

import com.example.ecommerce.model.PromoPopup;
import com.example.ecommerce.repository.CouponRepository;
import com.example.ecommerce.repository.PromoPopupRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** Offer pop-ups: the owner designs them, shoppers see the one that is live. */
@Service
public class PromoPopupService {
    /** What the owner sends. Enum fields arrive as text so a bad value is a clear message, not a 500. */
    public record Input(String name, String headline, String message, String couponCode, String ctaLabel, String ctaUrl,
                        String imageUrl, String theme, String trigger, String frequency, Integer delaySeconds,
                        Boolean countdown, LocalDateTime startsAt, LocalDateTime endsAt, Boolean active) {}

    /** What a shopper's browser needs, nothing internal. */
    public record Live(Long id, long version, String headline, String message, String couponCode, String ctaLabel, String ctaUrl,
                       String imageUrl, String theme, String trigger, String frequency, int delaySeconds, boolean countdown, LocalDateTime endsAt) {}

    @Autowired private PromoPopupRepository popups;
    @Autowired private CouponRepository coupons;
    @Autowired private MediaService media;
    @Autowired private AuditService audit;

    @Transactional(readOnly = true)
    public List<PromoPopup> list() { return popups.findAllByOrderByUpdatedAtDescIdDesc(); }

    @Transactional(readOnly = true)
    public Optional<Live> live() {
        LocalDateTime now = LocalDateTime.now();
        return popups.findByActiveTrueOrderByUpdatedAtDescIdDesc().stream()
                .filter(p -> (p.getStartsAt() == null || !p.getStartsAt().isAfter(now)) && (p.getEndsAt() == null || p.getEndsAt().isAfter(now)))
                .findFirst().map(PromoPopupService::toLive);
    }

    @Transactional
    public PromoPopup create(Input in) {
        PromoPopup p = new PromoPopup();
        apply(p, in);
        PromoPopup saved = popups.save(p);
        audit.record("POPUP_CREATED", "PromoPopup", saved.getId(), saved.getName());
        return saved;
    }

    @Transactional
    public PromoPopup update(Long id, Input in) {
        PromoPopup p = find(id);
        apply(p, in);
        p.touch();
        audit.record("POPUP_UPDATED", "PromoPopup", id, p.getName());
        return popups.save(p);
    }

    @Transactional
    public PromoPopup setActive(Long id, boolean active) {
        PromoPopup p = find(id);
        p.setActive(active);
        p.touch();
        audit.record("POPUP_ACTIVE_CHANGED", "PromoPopup", id, p.getName() + " active=" + active);
        return popups.save(p);
    }

    @Transactional
    public void delete(Long id) {
        PromoPopup p = find(id);
        popups.delete(p);
        audit.record("POPUP_DELETED", "PromoPopup", id, p.getName());
    }

    private PromoPopup find(Long id) {
        return popups.findById(id).orElseThrow(() -> new BusinessException("Pop-up not found"));
    }

    private void apply(PromoPopup p, Input in) {
        String name = text(in.name(), 80);
        String headline = text(in.headline(), 80);
        if (headline == null) throw new BusinessException("Write a headline (up to 80 characters)");
        if (name == null) name = headline;
        String message = text(in.message(), 240);
        if (in.message() != null && in.message().trim().length() > 240) throw new BusinessException("Keep the message under 240 characters");
        if (in.headline().trim().length() > 80) throw new BusinessException("Keep the headline under 80 characters");
        String code = in.couponCode() == null || in.couponCode().isBlank() ? null : CouponService.normalize(in.couponCode());
        if (code != null && coupons.findByCodeIgnoreCase(code).isEmpty()) {
            throw new BusinessException("The coupon code " + code + " does not exist. Create it under Coupons first");
        }
        String label = text(in.ctaLabel(), 30);
        String url = in.ctaUrl() == null || in.ctaUrl().isBlank() ? null : in.ctaUrl().trim();
        if (url != null && !(url.length() <= 200 && ((url.startsWith("/") && !url.startsWith("//")) || VideoService.isHttps(url)))) {
            throw new BusinessException("The button link must start with / (a page on your store) or be an https link");
        }
        if (url != null && label == null) throw new BusinessException("Give the button a label");
        String image = in.imageUrl() == null || in.imageUrl().isBlank() ? null : in.imageUrl().trim();
        if (image != null && !(image.length() <= 300 && ((image.startsWith("/") && !image.startsWith("//")) || VideoService.isHttps(image)))) {
            throw new BusinessException("The picture must be a file from your library or an https link");
        }
        if (image != null && image.startsWith("/api/media/") && !media.isLibraryLink(image, null)) {
            throw new BusinessException("That picture is not in your library");
        }
        int delay = in.delaySeconds() == null ? 5 : in.delaySeconds();
        if (delay < 0 || delay > 60) throw new BusinessException("The delay must be between 0 and 60 seconds");
        if (in.startsAt() != null && in.endsAt() != null && !in.endsAt().isAfter(in.startsAt())) {
            throw new BusinessException("The end time must be after the start time");
        }
        if (Boolean.TRUE.equals(in.countdown()) && in.endsAt() == null) throw new BusinessException("A countdown needs an end time");
        p.setName(name);
        p.setHeadline(headline);
        p.setMessage(message);
        p.setCouponCode(code);
        p.setCtaLabel(label);
        p.setCtaUrl(url);
        p.setImageUrl(image);
        p.setTheme(parse(PromoPopup.Theme.class, in.theme(), PromoPopup.Theme.MAROON));
        p.setTrigger(parse(PromoPopup.Trigger.class, in.trigger(), PromoPopup.Trigger.LOAD));
        p.setFrequency(parse(PromoPopup.Frequency.class, in.frequency(), PromoPopup.Frequency.SESSION));
        p.setDelaySeconds(delay);
        p.setCountdown(Boolean.TRUE.equals(in.countdown()));
        p.setStartsAt(in.startsAt());
        p.setEndsAt(in.endsAt());
        p.setActive(in.active() == null || in.active());
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String v, E fallback) {
        if (v == null || v.isBlank()) return fallback;
        try {
            return Enum.valueOf(type, v.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException("Unknown " + type.getSimpleName().toLowerCase() + ": " + v);
        }
    }

    private static String text(String s, int max) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : (t.length() > max ? t.substring(0, max) : t);
    }

    private static Live toLive(PromoPopup p) {
        long version = p.getUpdatedAt().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        return new Live(p.getId(), version, p.getHeadline(), p.getMessage(), p.getCouponCode(), p.getCtaLabel(), p.getCtaUrl(), p.getImageUrl(),
                p.getTheme().name(), p.getTrigger().name(), p.getFrequency().name(), p.getDelaySeconds(), p.isCountdown(), p.getEndsAt());
    }
}
