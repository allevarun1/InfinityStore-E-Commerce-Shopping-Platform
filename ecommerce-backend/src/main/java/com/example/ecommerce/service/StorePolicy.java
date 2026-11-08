package com.example.ecommerce.service;

import com.example.ecommerce.model.AppSetting;
import com.example.ecommerce.repository.AppSettingRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * The shop's delivery and payment rules in one place, so checkout, prices, messages and the stylist all agree.
 * The delivery fee and free-delivery threshold start from FREE_SHIPPING_THRESHOLD / SHIPPING_FEE, and the owner can
 * change them any time from the admin dashboard; that choice is saved in the database and wins over the environment.
 * COD_ENABLED=false turns off cash on delivery.
 */
@Component
public class StorePolicy {
    static final String KEY_FEE = "delivery.fee";
    static final String KEY_THRESHOLD = "delivery.free-threshold";
    private static final BigDecimal MAX = new BigDecimal("100000");

    private final boolean codEnabled;
    private final BigDecimal defaultThreshold;
    private final BigDecimal defaultFee;

    @Autowired(required = false)
    private AppSettingRepository settings;

    private volatile BigDecimal freeShippingThreshold;
    private volatile BigDecimal shippingFee;
    private volatile boolean loaded;

    public StorePolicy(@Value("${app.payment.cod-enabled:true}") boolean codEnabled,
                       @Value("${app.pricing.free-shipping-threshold:999}") String freeShippingThreshold,
                       @Value("${app.pricing.shipping-fee:49}") BigDecimal shippingFee) {
        this.codEnabled = codEnabled;
        BigDecimal t = freeShippingThreshold == null || freeShippingThreshold.isBlank() ? null : new BigDecimal(freeShippingThreshold.trim());
        this.defaultThreshold = t == null || t.signum() <= 0 ? null : t;
        this.defaultFee = shippingFee == null ? BigDecimal.ZERO : shippingFee;
        this.freeShippingThreshold = defaultThreshold;
        this.shippingFee = defaultFee;
    }

    public boolean codEnabled() {
        return codEnabled;
    }

    /** Orders above this ship free; null when the shop has no free shipping. */
    public BigDecimal freeShippingThreshold() {
        ensureLoaded();
        return freeShippingThreshold;
    }

    public boolean hasFreeShipping() {
        return freeShippingThreshold() != null;
    }

    public BigDecimal shippingFee() {
        ensureLoaded();
        return shippingFee;
    }

    /**
     * Saves the owner's delivery charges. A null or zero threshold means no free delivery; a zero fee means delivery is
     * always free.
     */
    public synchronized void updateDelivery(BigDecimal fee, BigDecimal threshold) {
        if (fee == null || fee.signum() < 0 || fee.compareTo(MAX) > 0) {
            throw new BusinessException("Enter a delivery charge between 0 and 100000");
        }
        if (threshold != null && (threshold.signum() < 0 || threshold.compareTo(MAX) > 0)) {
            throw new BusinessException("Enter a free-delivery amount between 0 and 100000");
        }
        BigDecimal t = threshold == null || threshold.signum() == 0 ? null : threshold.setScale(2, java.math.RoundingMode.HALF_UP);
        BigDecimal f = fee.setScale(2, java.math.RoundingMode.HALF_UP);
        if (settings != null) {
            settings.save(new AppSetting(KEY_FEE, f.toPlainString()));
            settings.save(new AppSetting(KEY_THRESHOLD, t == null ? "" : t.toPlainString()));
        }
        this.shippingFee = f;
        this.freeShippingThreshold = t;
        this.loaded = true;
        CatalogCache.invalidate(); // the public store info is cached; shoppers see the new charge straight away
    }

    /** Back to the values from the environment. */
    public synchronized void resetDelivery() {
        if (settings != null) {
            settings.deleteAllById(java.util.List.of(KEY_FEE, KEY_THRESHOLD));
        }
        this.shippingFee = defaultFee;
        this.freeShippingThreshold = defaultThreshold;
        this.loaded = true;
        CatalogCache.invalidate();
    }

    public BigDecimal defaultShippingFee() {
        return defaultFee;
    }

    public BigDecimal defaultFreeShippingThreshold() {
        return defaultThreshold;
    }

    private void ensureLoaded() {
        if (loaded || settings == null) return;
        synchronized (this) {
            if (loaded) return;
            try {
                settings.findById(KEY_FEE).ifPresent(s -> {
                    try { shippingFee = new BigDecimal(s.getValue().trim()); } catch (RuntimeException ignored) { }
                });
                settings.findById(KEY_THRESHOLD).ifPresent(s -> {
                    String v = s.getValue() == null ? "" : s.getValue().trim();
                    try { freeShippingThreshold = v.isEmpty() ? null : new BigDecimal(v); } catch (RuntimeException ignored) { }
                });
                loaded = true;
            } catch (RuntimeException e) {
                // Database not ready yet: keep the defaults and try again on the next read.
            }
        }
    }
}
