package com.example.ecommerce.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.ecommerce.model.Product;
import com.example.ecommerce.repository.ProductRepository;

/**
 * Opportunity Scout. Every finding is a rule applied to the store's own orders, stock, wishlists and
 * costs, with the numbers that triggered it. It makes no market predictions and takes no action:
 * the owner decides what to do with each suggestion.
 */
@Service
public class OpportunityService {
    public static final int RECENT_DAYS = 30;
    public static final int SLOW_DAYS = 60;
    public static final double LOW_MARGIN_PERCENT = 20.0;

    public record Opportunity(String type, String severity, Long productId, String title, String evidence,
                              String suggestedAction, Map<String, String> startDraftWith) {}

    public record Report(List<Opportunity> opportunities, List<String> notes, int ordersConsidered) {}

    @Autowired private ProductRepository products;
    @Autowired private InsightsService insights;

    @Transactional(readOnly = true)
    public Report scout() {
        List<Product> live = products.findPublic();
        Map<Long, Integer> sold30 = insights.unitsSold(RECENT_DAYS);
        Map<Long, Integer> sold60 = insights.unitsSold(SLOW_DAYS);
        Map<Long, Integer> wished = insights.wishlistCounts();
        List<Opportunity> out = new ArrayList<>();
        List<String> notes = new ArrayList<>();

        int totalUnits60 = sold60.values().stream().mapToInt(Integer::intValue).sum();
        if (totalUnits60 < 5) {
            notes.add("Only " + totalUnits60 + " units sold in the last " + SLOW_DAYS + " days, so sales-based findings are limited. "
                    + "Stock, cost and wishlist findings below do not depend on sales volume.");
        }

        List<String> missingCost = new ArrayList<>();
        for (Product p : live) {
            int stock = p.getStockQuantity() == null ? 0 : p.getStockQuantity();
            int s30 = sold30.getOrDefault(p.getId(), 0);
            int s60 = sold60.getOrDefault(p.getId(), 0);
            int saved = wished.getOrDefault(p.getId(), 0);

            if (s30 >= 3 && stock > 0) {
                double perDay = s30 / (double) RECENT_DAYS;
                double cover = stock / perDay;
                if (cover < 14) {
                    out.add(new Opportunity("RESTOCK", cover < 7 ? "HIGH" : "MEDIUM", p.getId(),
                            "Restock " + p.getName(),
                            s30 + " sold in " + RECENT_DAYS + " days; " + stock + " left is about " + Math.round(cover) + " days of cover at that pace.",
                            "Reorder from your supplier before it sells out.", null));
                }
            }
            if (stock == 0 && (s60 > 0 || saved > 0)) {
                out.add(new Opportunity("OUT_OF_STOCK_DEMAND", "HIGH", p.getId(),
                        p.getName() + " is sold out but still wanted",
                        s60 + " sold in " + SLOW_DAYS + " days and saved to " + saved + " wishlist" + (saved == 1 ? "" : "s") + ".",
                        "Restock it, or list a similar saree.", null));
            }
            if (stock > 0 && saved >= 2 && s60 == 0) {
                out.add(new Opportunity("WISHLIST_NO_PURCHASE", "MEDIUM", p.getId(),
                        "Saved by " + saved + " customers but not bought: " + p.getName(),
                        saved + " wishlists, no sales in " + SLOW_DAYS + " days.",
                        "Consider a limited-time deal; check the margin first.", null));
            }
            if (stock >= 5 && s60 == 0 && saved < 2) {
                out.add(new Opportunity("SLOW_MOVER", "LOW", p.getId(),
                        p.getName() + " has not sold in " + SLOW_DAYS + " days",
                        stock + " units in stock, no sales in " + SLOW_DAYS + " days.",
                        "Improve its photos and description, or run a deal.", null));
            }
            Double margin = ProductService.margin(p);
            if (margin != null && margin < LOW_MARGIN_PERCENT) {
                out.add(new Opportunity("LOW_MARGIN", margin < 0 ? "HIGH" : "MEDIUM", p.getId(),
                        p.getName() + " has a thin margin",
                        "Margin is " + margin + "% (price before GST against your recorded cost).",
                        margin < 0 ? "You are selling below cost. Raise the price or renegotiate cost." : "Review price or supplier cost.", null));
            }
            if (p.getCostPrice() == null) missingCost.add(p.getName());
        }

        if (!missingCost.isEmpty()) {
            out.add(new Opportunity("MISSING_COST", "MEDIUM", null,
                    missingCost.size() + " live product" + (missingCost.size() == 1 ? " has" : "s have") + " no cost recorded",
                    "Without cost, profit and margin cannot be measured. Examples: " + String.join(", ", missingCost.stream().limit(3).toList()) + ".",
                    "Add the cost per piece in each product's business details.", null));
        }

        gaps(live, sold60, "fabric", Product::getFabric, out);
        gaps(live, sold60, "occasion", Product::getOccasion, out);

        out.sort(Comparator.comparingInt((Opportunity o) -> rank(o.severity())).thenComparing(Opportunity::type));
        if (out.isEmpty()) notes.add("Nothing needs attention right now.");
        notes.add("These findings use only your store's own records. They are not market forecasts.");
        return new Report(out, notes, totalUnits60);
    }

    /** An attribute value that sells far more per listing than average is under-supplied. */
    private void gaps(List<Product> live, Map<Long, Integer> sold60, String attribute,
                      Function<Product, String> getter, List<Opportunity> out) {
        Map<String, int[]> byValue = new HashMap<>(); // [listings, units]
        for (Product p : live) {
            String v = getter.apply(p);
            if (v == null || v.isBlank()) continue;
            int[] row = byValue.computeIfAbsent(v.trim(), k -> new int[2]);
            row[0]++;
            row[1] += sold60.getOrDefault(p.getId(), 0);
        }
        int listings = byValue.values().stream().mapToInt(r -> r[0]).sum();
        int units = byValue.values().stream().mapToInt(r -> r[1]).sum();
        if (byValue.size() < 2 || listings == 0 || units < 5) return;
        double average = units / (double) listings;
        byValue.entrySet().stream()
                .filter(e -> e.getValue()[1] >= 5 && e.getValue()[1] / (double) e.getValue()[0] >= average * 1.5)
                .max(Comparator.comparingDouble(e -> e.getValue()[1] / (double) e.getValue()[0]))
                .ifPresent(e -> {
                    int[] r = e.getValue();
                    Map<String, String> prefill = new LinkedHashMap<>();
                    prefill.put(attribute, e.getKey());
                    out.add(new Opportunity("RANGE_GAP", "LOW", null,
                            "Add more " + e.getKey() + " sarees",
                            e.getKey() + " (" + attribute + ") sold " + r[1] + " units across " + r[0] + " listing" + (r[0] == 1 ? "" : "s")
                                    + " in " + SLOW_DAYS + " days, " + String.format("%.1f", r[1] / (double) r[0])
                                    + " per listing against " + String.format("%.1f", average) + " on average.",
                            "Source another " + e.getKey() + " saree and start a draft for it.", prefill));
                });
    }

    private static int rank(String severity) {
        return switch (severity) { case "HIGH" -> 0; case "MEDIUM" -> 1; default -> 2; };
    }
}
