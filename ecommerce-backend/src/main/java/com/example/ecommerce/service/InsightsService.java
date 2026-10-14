package com.example.ecommerce.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.ecommerce.model.Order;
import com.example.ecommerce.model.OrderItem;
import com.example.ecommerce.model.Product;
import com.example.ecommerce.repository.OrderRepository;
import com.example.ecommerce.repository.ProductRepository;
import com.example.ecommerce.repository.WishlistItemRepository;

/**
 * Admin-only business intelligence computed from the store's own records. Nothing here is estimated
 * from outside data: every figure traces to orders, stock or wishlists, and profit is reported only
 * for lines whose cost was known when the order was placed.
 */
@Service
public class InsightsService {
    public static final int LOW_STOCK_AT = 5;

    public record DailyPoint(LocalDate date, int orders, BigDecimal netSales) {}

    public record ProductPerformance(Long productId, String name, int units, BigDecimal netSales, BigDecimal grossProfit, Double marginPercent) {}

    public record StockAlert(Long productId, String name, int stock) {}

    public record Dashboard(
            int days, int orders, int units, BigDecimal netSales, BigDecimal gstCollected, BigDecimal shippingCharged,
            BigDecimal orderTotal, BigDecimal averageOrderValue, BigDecimal grossProfit, Double costCoveragePercent,
            long pendingOrders, int cancelledOrders, int testOrdersExcluded,
            List<ProductPerformance> topProducts, List<StockAlert> lowStock, List<StockAlert> outOfStock,
            List<DailyPoint> daily, Map<String, Integer> paymentMix, List<String> notes) {}

    @Autowired private OrderRepository orders;
    @Autowired private ProductRepository products;
    @Autowired private WishlistItemRepository wishlists;

    /** Orders that count as sales: not cancelled, not refunded, not placed through the test payment provider. */
    public static boolean counts(Order o) {
        return !"CANCELLED".equals(o.getStatus()) && !"REFUNDED".equals(o.getPaymentStatus()) && !o.isTestPayment();
    }

    @Transactional(readOnly = true)
    public Dashboard dashboard(int days) {
        int window = Math.max(1, Math.min(days, 365));
        LocalDateTime from = LocalDate.now().minusDays(window - 1L).atStartOfDay();
        List<Order> inRange = orders.findSince(from);

        int orderCount = 0, units = 0, cancelled = 0, testExcluded = 0;
        BigDecimal net = BigDecimal.ZERO, gst = BigDecimal.ZERO, ship = BigDecimal.ZERO, total = BigDecimal.ZERO;
        BigDecimal costedNet = BigDecimal.ZERO, costedCost = BigDecimal.ZERO;
        Map<LocalDate, int[]> dayOrders = new TreeMap<>();
        Map<LocalDate, BigDecimal> daySales = new HashMap<>();
        Map<Long, int[]> unitsBy = new HashMap<>();
        Map<Long, BigDecimal[]> moneyBy = new HashMap<>(); // [net, costedNet, costedCost]
        Map<Long, String> names = new HashMap<>();
        Map<String, Integer> mix = new LinkedHashMap<>();

        for (int i = 0; i < window; i++) {
            LocalDate d = from.toLocalDate().plusDays(i);
            dayOrders.put(d, new int[1]);
            daySales.put(d, BigDecimal.ZERO);
        }

        for (Order o : inRange) {
            if ("CANCELLED".equals(o.getStatus())) cancelled++;
            if (o.isTestPayment()) testExcluded++;
            if (!counts(o)) continue;

            orderCount++;
            total = total.add(nz(o.getTotalPrice()));
            gst = gst.add(nz(o.getTaxAmount()));
            ship = ship.add(nz(o.getShippingAmount()));
            mix.merge(o.getPaymentMethod() == null ? "UNKNOWN" : o.getPaymentMethod(), 1, Integer::sum);

            BigDecimal orderNet = BigDecimal.ZERO;
            for (OrderItem it : o.getOrderItems()) {
                BigDecimal line = it.getPrice().multiply(BigDecimal.valueOf(it.getQuantity()));
                orderNet = orderNet.add(line);
                units += it.getQuantity();
                Long pid = it.getProduct().getId();
                names.putIfAbsent(pid, it.getProductName() != null ? it.getProductName() : it.getProduct().getName());
                unitsBy.computeIfAbsent(pid, k -> new int[1])[0] += it.getQuantity();
                BigDecimal[] m = moneyBy.computeIfAbsent(pid, k -> new BigDecimal[] {BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO});
                m[0] = m[0].add(line);
                if (it.getUnitCost() != null) {
                    BigDecimal cost = it.getUnitCost().multiply(BigDecimal.valueOf(it.getQuantity()));
                    m[1] = m[1].add(line);
                    m[2] = m[2].add(cost);
                    costedNet = costedNet.add(line);
                    costedCost = costedCost.add(cost);
                }
            }
            net = net.add(orderNet);
            LocalDate day = o.getOrderDate().toLocalDate();
            if (dayOrders.containsKey(day)) {
                dayOrders.get(day)[0]++;
                daySales.merge(day, orderNet, BigDecimal::add);
            }
        }

        List<ProductPerformance> top = moneyBy.entrySet().stream()
                .sorted((a, b) -> b.getValue()[0].compareTo(a.getValue()[0]))
                .limit(5)
                .map(e -> {
                    BigDecimal[] m = e.getValue();
                    boolean costed = m[1].signum() > 0;
                    BigDecimal profit = costed ? m[1].subtract(m[2]) : null;
                    Double margin = costed ? profit.multiply(BigDecimal.valueOf(100)).divide(m[1], 1, RoundingMode.HALF_UP).doubleValue() : null;
                    return new ProductPerformance(e.getKey(), names.get(e.getKey()), unitsBy.get(e.getKey())[0], m[0], profit, margin);
                }).toList();

        List<StockAlert> low = new ArrayList<>();
        List<StockAlert> out = new ArrayList<>();
        for (Product p : products.findPublic()) {
            int stock = p.getStockQuantity() == null ? 0 : p.getStockQuantity();
            if (stock <= 0) out.add(new StockAlert(p.getId(), p.getName(), 0));
            else if (stock <= LOW_STOCK_AT) low.add(new StockAlert(p.getId(), p.getName(), stock));
        }
        low.sort(Comparator.comparingInt(StockAlert::stock));

        List<DailyPoint> series = dayOrders.entrySet().stream()
                .map(e -> new DailyPoint(e.getKey(), e.getValue()[0], daySales.get(e.getKey()))).toList();

        BigDecimal aov = orderCount == 0 ? BigDecimal.ZERO : total.divide(BigDecimal.valueOf(orderCount), 2, RoundingMode.HALF_UP);
        BigDecimal profit = costedNet.signum() > 0 ? costedNet.subtract(costedCost) : null;
        Double coverage = net.signum() > 0
                ? costedNet.multiply(BigDecimal.valueOf(100)).divide(net, 1, RoundingMode.HALF_UP).doubleValue() : null;

        List<String> notes = new ArrayList<>();
        notes.add("Net sales exclude GST and shipping. Cancelled, refunded and test-payment orders are not counted.");
        if (coverage != null && coverage < 100) {
            notes.add("Profit covers only " + coverage + "% of sales: the rest had no cost recorded when ordered.");
        }
        if (orderCount == 0) notes.add("No counted orders in this period yet.");

        return new Dashboard(window, orderCount, units, net, gst, ship, total, aov, profit, coverage,
                orders.countByStatus("PENDING"), cancelled, testExcluded, top, low, out, series, mix, notes);
    }

    private static BigDecimal nz(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }

    // ------------------------------------------------------------------ used by OpportunityService

    /** Units sold per product within the window, counted orders only. */
    @Transactional(readOnly = true)
    public Map<Long, Integer> unitsSold(int days) {
        LocalDateTime from = LocalDate.now().minusDays(days - 1L).atStartOfDay();
        Map<Long, Integer> sold = new HashMap<>();
        for (Order o : orders.findSince(from)) {
            if (!counts(o)) continue;
            for (OrderItem it : o.getOrderItems()) sold.merge(it.getProduct().getId(), it.getQuantity(), Integer::sum);
        }
        return sold;
    }

    @Transactional(readOnly = true)
    public Map<Long, Integer> wishlistCounts() {
        Map<Long, Integer> m = new HashMap<>();
        for (Object[] row : wishlists.countsByProduct()) m.put((Long) row[0], ((Number) row[1]).intValue());
        return m;
    }
}
