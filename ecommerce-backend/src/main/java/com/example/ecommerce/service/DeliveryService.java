package com.example.ecommerce.service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Delivery estimate for a PIN code. The ranges are the owner's own settings (ask your courier and set
 * them), grouped by the first two digits of the PIN: big cities, remote regions, and everything else.
 * The result is always labelled an estimate.
 */
@Service
public class DeliveryService {
    private static final Pattern PIN = Pattern.compile("^[1-9]\\d{5}$");

    public record Estimate(String pincode, boolean serviceable, boolean codAvailable, String zone, int minDays, int maxDays,
                           LocalDate earliest, LocalDate latest, String message) {}

    private final List<String> metro, remote, blocked, noCod;
    private final int[] metroDays, remoteDays, defaultDays;
    private final int dispatchDays;
    private final ZoneId zone;

    public DeliveryService(
            @Value("${app.delivery.metro-prefixes:11,30,38,40,41,50,56,60,70}") String metro,
            @Value("${app.delivery.remote-prefixes:18,19,74,78,79,90}") String remote,
            @Value("${app.delivery.blocked-prefixes:}") String blocked,
            @Value("${app.delivery.no-cod-prefixes:}") String noCod,
            @Value("${app.delivery.metro-days:3-5}") String metroDays,
            @Value("${app.delivery.remote-days:8-12}") String remoteDays,
            @Value("${app.delivery.default-days:5-8}") String defaultDays,
            @Value("${app.delivery.dispatch-days:1}") int dispatchDays,
            @Value("${app.automation.zone:Asia/Kolkata}") String zone) {
        this.metro = split(metro);
        this.remote = split(remote);
        this.blocked = split(blocked);
        this.noCod = split(noCod);
        this.metroDays = range(metroDays, 3, 5);
        this.remoteDays = range(remoteDays, 8, 12);
        this.defaultDays = range(defaultDays, 5, 8);
        this.dispatchDays = Math.max(0, dispatchDays);
        this.zone = ZoneId.of(zone);
    }

    public Estimate estimate(String rawPin) {
        String pin = rawPin == null ? "" : rawPin.trim();
        if (!PIN.matcher(pin).matches()) throw new BusinessException("Enter a valid 6-digit PIN code");
        String prefix = pin.substring(0, 2);
        if (blocked.contains(prefix)) {
            return new Estimate(pin, false, false, "unserviceable", 0, 0, null, null,
                    "Sorry, we can't deliver to " + pin + " yet. Message us on WhatsApp and we'll see what we can do.");
        }
        String zoneName;
        int[] days;
        if (remote.contains(prefix)) { zoneName = "remote"; days = remoteDays; }
        else if (metro.contains(prefix)) { zoneName = "metro"; days = metroDays; }
        else { zoneName = "standard"; days = defaultDays; }
        LocalDate today = LocalDate.now(zone);
        LocalDate from = today.plusDays(dispatchDays + days[0]);
        LocalDate to = today.plusDays(dispatchDays + days[1]);
        boolean cod = !noCod.contains(prefix);
        return new Estimate(pin, true, cod, zoneName, days[0], days[1], from, to,
                "Estimated delivery " + from.getDayOfWeek().getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH)
                        + " " + from.getDayOfMonth() + " " + from.getMonth().getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH)
                        + " to " + to.getDayOfWeek().getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH)
                        + " " + to.getDayOfMonth() + " " + to.getMonth().getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH)
                        + (cod ? "" : ". Cash on delivery is not available here."));
    }

    private static List<String> split(String v) {
        return v == null || v.isBlank() ? List.of() : Arrays.stream(v.split(",")).map(String::trim).filter(s -> s.length() == 2).toList();
    }

    private static int[] range(String v, int dMin, int dMax) {
        try {
            String[] p = v.trim().split("-");
            int a = Integer.parseInt(p[0].trim());
            int b = p.length > 1 ? Integer.parseInt(p[1].trim()) : a;
            return a > 0 && b >= a ? new int[] {a, b} : new int[] {dMin, dMax};
        } catch (RuntimeException e) {
            return new int[] {dMin, dMax};
        }
    }
}
