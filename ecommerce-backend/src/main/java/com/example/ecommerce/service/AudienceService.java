package com.example.ecommerce.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.ecommerce.model.ERole;
import com.example.ecommerce.model.Lead;
import com.example.ecommerce.model.Order;
import com.example.ecommerce.model.OrderItem;
import com.example.ecommerce.model.Product;
import com.example.ecommerce.model.StockRequest;
import com.example.ecommerce.model.User;
import com.example.ecommerce.model.WishlistItem;
import com.example.ecommerce.repository.LeadRepository;
import com.example.ecommerce.repository.OrderRepository;
import com.example.ecommerce.repository.ProductRepository;
import com.example.ecommerce.repository.StockRequestRepository;
import com.example.ecommerce.repository.UserRepository;
import com.example.ecommerce.repository.WishlistItemRepository;

/**
 * Who to tell about a saree, and who the customers are. Everything the shop already knows is used: who saved the
 * saree, who asked to hear when it is back, who asked about it, who bought from the same category or fabric, and who
 * buys often. Only people with a phone number are returned (WhatsApp needs one); the owner sends each message by hand.
 */
@Service
public class AudienceService {

    public record Person(String key, String name, String phone, int score, List<String> reasons, int orders,
                         BigDecimal spent, LocalDateTime lastOrderAt) {}

    public record Customer(Long id, String username, String name, String email, String phone, int orders, BigDecimal spent,
                           LocalDateTime lastOrderAt, LocalDateTime firstOrderAt, int saved, boolean admin, String favouriteCategory) {}

    @Autowired private ProductRepository products;
    @Autowired private OrderRepository orders;
    @Autowired private WishlistItemRepository wishlist;
    @Autowired private StockRequestRepository stockRequests;
    @Autowired private LeadRepository leads;
    @Autowired private UserRepository users;

    /** Indian mobile numbers come in many shapes (+91, 0-prefixed, spaced); keep the 10 digits WhatsApp needs. */
    static String phone(String raw) {
        if (raw == null) return null;
        String d = raw.replaceAll("\\D", "");
        if (d.length() > 10 && (d.startsWith("91") || d.startsWith("0"))) d = d.substring(d.length() - 10);
        return d.length() == 10 && "6789".indexOf(d.charAt(0)) >= 0 ? d : null;
    }

    private static String nameOf(User u) {
        String n = ((u.getFirstName() == null ? "" : u.getFirstName()) + " " + (u.getLastName() == null ? "" : u.getLastName())).trim();
        return n.isEmpty() ? u.getUsername() : n;
    }

    private static boolean counts(Order o) {
        return !"CANCELLED".equals(o.getStatus()) && !o.isTestPayment();
    }

    private static final class Acc {
        String name; String phone; int score; final Map<String, Integer> reasons = new LinkedHashMap<>();
        int orders; BigDecimal spent = BigDecimal.ZERO; LocalDateTime last;
        void add(int points, String why) { if (reasons.putIfAbsent(why, points) == null) score += points; }
        /** Strongest reason first, so the list can show the top few. */
        List<String> why() { return reasons.entrySet().stream().sorted(Map.Entry.<String, Integer>comparingByValue().reversed()).map(Map.Entry::getKey).toList(); }
    }

    @Transactional(readOnly = true)
    public List<Person> audienceFor(Long productId) {
        Product p = products.findById(productId).orElseThrow(() -> new BusinessException("Saree not found"));
        String category = p.getCategory() == null ? null : p.getCategory().getName();
        String fabric = p.getFabric();
        String catWord = category == null ? null : category.replaceAll("(?i)\\s*sarees?$", "").trim();
        // One entry per person. A customer with an account is one person however many phone numbers they used
        // (profile, checkout); a lead without an account is known by their phone, and joins the account if it matches.
        Map<String, Acc> people = new LinkedHashMap<>();
        Map<String, String> byPhone = new HashMap<>();
        java.util.function.Function<String, Acc> entry = (key) -> people.computeIfAbsent(key, k -> new Acc());

        List<Order> all = orders.findAllByOrderByOrderDateDesc();     // newest first: the latest checkout phone wins
        Map<Long, String> bestPhone = new HashMap<>();
        for (Order o : all) {
            if (o.getUser() == null) continue;
            String ph = phone(o.getPhoneNumber());
            if (ph != null) { bestPhone.putIfAbsent(o.getUser().getId(), ph); byPhone.putIfAbsent(ph, "u" + o.getUser().getId()); }
        }
        java.util.function.Function<User, Acc> forUser = (u) -> {
            String key = "u" + u.getId();
            String ph = bestPhone.getOrDefault(u.getId(), phone(u.getPhone()));
            if (phone(u.getPhone()) != null) byPhone.putIfAbsent(phone(u.getPhone()), key);
            Acc a = entry.apply(key);
            if (a.phone == null) a.phone = ph;
            if (a.name == null) a.name = nameOf(u);
            return a;
        };
        java.util.function.BiFunction<String, String, Acc> forPhone = (ph, name) -> {
            Acc a = entry.apply(byPhone.getOrDefault(ph, ph));
            if (a.phone == null) a.phone = ph;
            if (a.name == null && name != null && !name.isBlank()) a.name = name;
            return a;
        };

        Set<Acc> alreadyBought = new java.util.HashSet<>();
        for (Order o : all) {
            if (!counts(o)) continue;
            Acc a;
            if (o.getUser() != null) a = forUser.apply(o.getUser());
            else { String ph = phone(o.getPhoneNumber()); if (ph == null) continue; a = forPhone.apply(ph, null); }
            a.orders++;
            a.spent = a.spent.add(o.getTotalPrice() == null ? BigDecimal.ZERO : o.getTotalPrice());
            if (a.last == null || (o.getOrderDate() != null && o.getOrderDate().isAfter(a.last))) a.last = o.getOrderDate();
            for (OrderItem it : o.getOrderItems()) {
                Product q = it.getProduct();
                if (q == null) continue;
                if (q.getId().equals(p.getId())) alreadyBought.add(a);
                if (category != null && q.getCategory() != null && category.equals(q.getCategory().getName()))
                    a.add(25, "Bought " + catWord.toLowerCase(Locale.ROOT) + " before");
                else if (fabric != null && fabric.equalsIgnoreCase(q.getFabric()))
                    a.add(12, "Likes " + fabric.toLowerCase(Locale.ROOT));
            }
        }
        for (Acc a : people.values()) {
            if (a.orders >= 2) a.add(10, "Repeat customer");
            else if (a.orders == 1) a.add(4, "Past customer");
            if (a.last != null && a.last.isAfter(LocalDateTime.now().minusDays(90))) a.add(6, "Bought in the last 3 months");
        }

        for (WishlistItem w : wishlist.findAll()) {
            if (w.getProduct() == null || !w.getProduct().getId().equals(p.getId()) || w.getUser() == null) continue;
            forUser.apply(w.getUser()).add(50, "Saved this saree");
        }
        for (StockRequest sr : stockRequests.findAll()) {
            if (!p.getId().equals(sr.getProductId()) || sr.getLead() == null) continue;
            String ph = phone(sr.getLead().getPhone());
            if (ph != null) forPhone.apply(ph, sr.getLead().getName()).add(45, "Asked to hear when it is back");
        }
        for (Lead l : leads.findAll()) {
            String ph = phone(l.getPhone());
            if (ph == null) continue;
            if (Lead.LOST.equals(l.getStatus())) { alreadyBought.add(forPhone.apply(ph, l.getName())); continue; }   // said no: never suggest them
            if (p.getId().equals(l.getProductId())) forPhone.apply(ph, l.getName()).add(40, "Asked about this saree");
            else if (l.getInterest() != null && catWord != null && l.getInterest().toLowerCase(Locale.ROOT).contains(catWord.toLowerCase(Locale.ROOT)))
                forPhone.apply(ph, l.getName()).add(15, "Interested in " + catWord.toLowerCase(Locale.ROOT));
            else if (people.containsKey(byPhone.getOrDefault(ph, ph))) forPhone.apply(ph, l.getName()).add(3, "Opted in to hear from you");
        }

        List<Person> out = new ArrayList<>();
        for (Map.Entry<String, Acc> e : people.entrySet()) {
            Acc a = e.getValue();
            if (alreadyBought.contains(a) || a.reasons.isEmpty() || a.phone == null) continue;
            out.add(new Person(e.getKey(), a.name == null ? "Customer" : a.name, a.phone, a.score, a.why(), a.orders, a.spent, a.last));
        }
        out.sort(Comparator.comparingInt(Person::score).reversed().thenComparing(Person::name));
        return out.size() > 200 ? out.subList(0, 200) : out;
    }

    /** Every account with what it is worth to the shop: orders, spend, last order, saved sarees, favourite category. */
    @Transactional(readOnly = true)
    public List<Customer> customers() {
        Map<Long, List<Order>> byUser = new HashMap<>();
        for (Order o : orders.findAllByOrderByOrderDateDesc()) {
            if (o.getUser() != null && counts(o)) byUser.computeIfAbsent(o.getUser().getId(), k -> new ArrayList<>()).add(o);
        }
        Map<Long, Integer> saved = new HashMap<>();
        for (WishlistItem w : wishlist.findAll()) if (w.getUser() != null) saved.merge(w.getUser().getId(), 1, Integer::sum);
        List<Customer> out = new ArrayList<>();
        for (User u : users.findAll()) {
            List<Order> os = byUser.getOrDefault(u.getId(), List.of());
            BigDecimal spent = os.stream().map(o -> o.getTotalPrice() == null ? BigDecimal.ZERO : o.getTotalPrice()).reduce(BigDecimal.ZERO, BigDecimal::add);
            Map<String, Integer> cats = new HashMap<>();
            for (Order o : os) for (OrderItem it : o.getOrderItems())
                if (it.getProduct() != null && it.getProduct().getCategory() != null) cats.merge(it.getProduct().getCategory().getName(), it.getQuantity(), Integer::sum);
            String fav = cats.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
            // the number they last gave at checkout is the one they use; fall back to the profile
            String ph = os.stream().map(o -> phone(o.getPhoneNumber())).filter(java.util.Objects::nonNull).findFirst().orElse(phone(u.getPhone()));
            boolean admin = u.getRoles().stream().anyMatch(r -> r.getName() == ERole.ROLE_ADMIN);
            out.add(new Customer(u.getId(), u.getUsername(), nameOf(u), u.getEmail(), ph, os.size(), spent,
                    os.isEmpty() ? null : os.get(0).getOrderDate(), os.isEmpty() ? null : os.get(os.size() - 1).getOrderDate(),
                    saved.getOrDefault(u.getId(), 0), admin, fav));
        }
        out.sort(Comparator.comparing(Customer::admin).thenComparing(Customer::spent, Comparator.reverseOrder()).thenComparing(Customer::username));
        return out;
    }
}
