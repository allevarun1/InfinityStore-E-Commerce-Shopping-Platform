package com.example.ecommerce.service.assistant;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.ecommerce.model.Product;
import com.example.ecommerce.repository.ProductRepository;
import com.example.ecommerce.service.CouponService;
import com.example.ecommerce.service.payment.PaymentGateway;

/**
 * The storefront shopping assistant. It understands budget, occasion, fabric and colour in plain words
 * ("red silk for a wedding under 5k") and answers the common shop questions. Recommendations always come
 * from the live catalogue by rule, so it can never suggest a saree that does not exist or quote a wrong
 * price. When an AI provider is configured it only rewords the reply, and the reworded text is checked.
 */
@Service
public class ShopAssistantService {
    private static final Logger log = LoggerFactory.getLogger(ShopAssistantService.class);
    private static final int MAX_RESULTS = 4;

    /** What the shopper has asked for so far. The browser sends it back with each message. */
    public record Filters(BigDecimal minPrice, BigDecimal maxPrice, String occasion, String fabric, String colour, Boolean dealsOnly) {
        static Filters empty() { return new Filters(null, null, null, null, null, null); }
        boolean isEmpty() { return minPrice == null && maxPrice == null && occasion == null && fabric == null && colour == null && !Boolean.TRUE.equals(dealsOnly); }
    }

    public record Request(String message, Filters filters) {}

    public record ProductCard(Long id, String name, String imageUrl, BigDecimal price, BigDecimal dealPrice, boolean deal,
                              int stockQuantity, boolean inStock, String why) {}

    public record Reply(String reply, List<ProductCard> products, List<String> suggestions, Filters filters,
                        boolean offerContact, String intent, String link, String linkLabel, String writer) {
        /** What the shopper is looking for, in words, e.g. "red silk sarees for a wedding under 5,000". Saved on their lead. */
        public String summary() { return filters == null || filters.isEmpty() ? null : describe(filters); }
    }

    /** Shopper words for each occasion. A product matches when its occasion, name or description uses any of them. */
    static final Map<String, List<String>> OCCASIONS = new LinkedHashMap<>();
    static final Map<String, List<String>> FABRICS = new LinkedHashMap<>();
    static final List<String> COLOURS = List.of("red", "maroon", "wine", "pink", "rani", "magenta", "peach", "orange", "yellow", "mustard",
            "gold", "golden", "green", "bottle green", "parrot green", "mint", "teal", "blue", "navy", "royal blue", "sky blue", "turquoise",
            "purple", "lavender", "violet", "indigo", "emerald", "coral", "mauve", "olive", "onion pink", "white", "off-white", "cream", "beige", "ivory", "silver", "grey", "black", "brown", "copper", "rust");

    static {
        OCCASIONS.put("wedding", List.of("wedding", "bridal", "bride", "marriage", "shaadi", "reception", "sangeet", "mehendi", "engagement", "muhurtham"));
        OCCASIONS.put("festive", List.of("festive", "festival", "diwali", "pooja", "puja", "dasara", "dussehra", "navratri", "onam", "pongal", "durga", "ugadi", "karva"));
        OCCASIONS.put("party", List.of("party", "cocktail", "evening", "function", "celebration"));
        OCCASIONS.put("office", List.of("office", "work", "formal", "meeting", "corporate"));
        OCCASIONS.put("daily", List.of("daily", "everyday", "casual", "regular"));

        FABRICS.put("silk", List.of("silk", "pattu", "kanjivaram", "kanjeevaram", "kanchipuram", "banarasi", "benarasi", "mysore", "paithani", "tussar"));
        FABRICS.put("cotton", List.of("cotton", "chanderi", "khadi", "mulmul", "handloom cotton"));
        FABRICS.put("georgette", List.of("georgette"));
        FABRICS.put("chiffon", List.of("chiffon"));
        FABRICS.put("linen", List.of("linen"));
        FABRICS.put("crepe", List.of("crepe"));
        FABRICS.put("organza", List.of("organza", "tissue"));
        FABRICS.put("net", List.of("net"));
    }

    private static final Pattern RANGE = Pattern.compile("(?:between|from)?\\s*(?:rs\\.?|₹|inr)?\\s*(\\d[\\d,]*(?:\\.\\d+)?)\\s*(k)?\\s*(?:-|to|and)\\s*(?:rs\\.?|₹|inr)?\\s*(\\d[\\d,]*(?:\\.\\d+)?)\\s*(k)?");
    private static final Pattern UNDER = Pattern.compile("(?:under|below|less than|within|upto|up to|max(?:imum)?|budget(?: is| of)?|not more than|around|<)\\s*(?:rs\\.?|₹|inr)?\\s*(\\d[\\d,]*(?:\\.\\d+)?)\\s*(k)?");
    private static final Pattern OVER = Pattern.compile("(?:over|above|more than|min(?:imum)?|starting|>)\\s*(?:rs\\.?|₹|inr)?\\s*(\\d[\\d,]*(?:\\.\\d+)?)\\s*(k)?");
    private static final Pattern BARE_PRICE = Pattern.compile("(?:rs\\.?|₹|inr)\\s*(\\d[\\d,]*)\\s*(k)?|(\\d+(?:\\.\\d+)?)\\s*k\\b");

    @Autowired private ProductRepository products;
    @Autowired private CouponService coupons;
    @Autowired private PaymentGateway payments;
    @Autowired private AnthropicAssistantWriter ai;

    @Value("${app.pricing.free-shipping-threshold:999}") private BigDecimal freeShippingThreshold;
    @Value("${app.pricing.shipping-fee:49}") private BigDecimal shippingFee;
    @Value("${app.growth.welcome-code:WELCOME10}") private String welcomeCode;
    @Value("${app.store.whatsapp:}") private String whatsapp;

    @Transactional(readOnly = true)
    public Reply chat(Request request) {
        String raw = request.message() == null ? "" : request.message().trim();
        if (raw.length() > 500) raw = raw.substring(0, 500);
        String text = " " + raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9₹.,\\- ]", " ").replaceAll("\\s+", " ") + " ";
        Filters prior = request.filters() == null ? Filters.empty() : request.filters();

        boolean reset = has(text, "start over", "reset", "clear", "new search", "something else");
        if (reset) prior = Filters.empty();

        // Shop questions first: they do not change the search.
        Reply faq = faq(text, prior);
        if (faq != null) return faq;

        Filters f = merge(prior, parse(text));
        boolean greeting = f.isEmpty() && (reset || has(text, "hi", "hello", "hey", "namaste", "vanakkam", "good morning", "good evening") || raw.isBlank());
        if (greeting) {
            return new Reply("Namaste! I'm the Rekha Shopping Mall stylist. Tell me the occasion, your budget or a colour you love, and I'll pick sarees for you.",
                    List.of(), List.of("Wedding sarees", "Under ₹3,000", "Silk sarees", "Today's deals"), f, false, "greeting", null, null, "rules");
        }
        if (f.isEmpty()) {
            return new Reply("I can help you find the right saree. Try something like \"red silk for a wedding under ₹5,000\". Or leave your number and our team will help you personally.",
                    bestsellers(), List.of("Wedding sarees", "Office wear", "Under ₹3,000", "Today's deals"), f, true, "unknown", null, null, "rules");
        }
        return recommend(raw, f);
    }

    private Reply recommend(String raw, Filters f) {
        List<Product> live = products.findPublic();
        List<String> relaxed = new ArrayList<>();
        Filters attempt = f;
        List<Product> found = filter(live, attempt);
        // Nothing matches everything: drop the softest wish first and say so.
        if (found.isEmpty() && attempt.colour() != null) { relaxed.add(attempt.colour()); attempt = new Filters(attempt.minPrice(), attempt.maxPrice(), attempt.occasion(), attempt.fabric(), null, attempt.dealsOnly()); found = filter(live, attempt); }
        if (found.isEmpty() && attempt.occasion() != null) { relaxed.add("for " + attempt.occasion()); attempt = new Filters(attempt.minPrice(), attempt.maxPrice(), null, attempt.fabric(), null, attempt.dealsOnly()); found = filter(live, attempt); }
        if (found.isEmpty() && attempt.fabric() != null) { relaxed.add(attempt.fabric()); attempt = new Filters(attempt.minPrice(), attempt.maxPrice(), null, null, null, attempt.dealsOnly()); found = filter(live, attempt); }

        List<ProductCard> cards = found.stream()
                .sorted(Comparator.comparing((Product p) -> stock(p) > 0 ? 0 : 1)
                        .thenComparing(p -> Boolean.TRUE.equals(p.getDeal()) ? 0 : 1)
                        .thenComparing(p -> -(p.getRating() == null ? 0 : p.getRating())))
                .limit(MAX_RESULTS).map(this::card).toList();

        String what = describe(f);
        String reply;
        boolean offerContact = false;
        if (cards.isEmpty()) {
            reply = "I couldn't find " + what + " in the shop right now. Leave your WhatsApp number or email and we'll tell you as soon as something like it arrives.";
            offerContact = true;
        } else if (!relaxed.isEmpty()) {
            reply = "I couldn't find exactly " + what + " right now, so here are the closest matches (I left out " + String.join(", ", relaxed) + "):";
            offerContact = true;
        } else {
            reply = "Here " + (cards.size() == 1 ? "is 1 of our " : "are " + cards.size() + " ") + what + " you might love:";
        }
        if (Boolean.TRUE.equals(f.dealsOnly())) {
            String code = offerCode();
            if (code != null) reply += " New here? Code " + code + " takes a little more off your first order.";
        }

        String writer = "rules";
        if (ai.configured() && !cards.isEmpty()) {
            try {
                String reworded = ai.reword(raw, reply, found.stream().limit(MAX_RESULTS).toList());
                if (reworded != null) { reply = reworded; writer = "anthropic"; }
            } catch (Exception e) {
                log.warn("Assistant AI writer failed: {}", e.getClass().getSimpleName());
            }
        }
        String link = "/?" + query(f) + "#products";
        return new Reply(reply, cards, suggestions(f, found), f, offerContact, "recommend", link, "See all matches", writer);
    }

    private Reply faq(String t, Filters f) {
        String fee = money(shippingFee), free = money(freeShippingThreshold);
        if (has(t, "track", "where is my order", "order status", "my order", "delivered yet", "not received")) {
            return info("You can follow any order with just the order number and the mobile number you gave at checkout, no sign-in needed. If something looks wrong, message us and we'll check right away.", f, "track", "/track", "Track my order", true);
        }
        if (has(t, "return", "exchange", "refund", "replace")) {
            return info("Our return and exchange rules are on the Shipping & returns page. If a saree arrives damaged, message us with a photo and we'll sort it out.", f, "returns", "/shipping", "Shipping & returns", true);
        }
        if (has(t, "blouse", "length", "metre", "meter", "size")) {
            return info("Each saree's page lists its length and whether a blouse piece is included. Ask me for an occasion or fabric and I'll show you options.", f, "size", null, null, false);
        }
        if (has(t, "wash", "care", "dry clean", "clean")) {
            return info("Silk and zari sarees are best dry-cleaned. Cotton can usually be hand-washed in cold water. Each product page lists its own care instructions.", f, "care", null, null, false);
        }
        if (has(t, "cod", "cash on delivery", "payment", "pay online", "upi", "card")) {
            String online = payments.enabled() ? " You can also pay online at checkout." : " Pay in cash when your saree arrives.";
            return info("Yes, cash on delivery is available on every order." + online, f, "payment", null, null, false);
        }
        if (has(t, "shipping", "delivery", "deliver", "courier", "dispatch", "how long", "how many days")) {
            return info("Delivery is free on orders over " + free + "; below that it's " + fee + ". Delivery times by area are on the Shipping page.", f, "shipping", "/shipping", "Shipping details", false);
        }
        if (has(t, "talk to", "call me", "human", "person", "agent", "speak", "contact", "help me choose", "whatsapp")) {
            String how = whatsapp != null && !whatsapp.isBlank() ? " or tap the WhatsApp button" : "";
            return info("Happy to help personally! Leave your number or email below" + how + " and our team will get back to you.", f, "contact", null, null, true);
        }
        if (has(t, "coupon", "code", "discount", "offer") && !has(t, "saree", "silk", "cotton", "wedding")) {
            String code = offerCode();
            String msg = code != null ? "Use code " + code + " at checkout for a discount on your first order. Want to see today's deals too?"
                    : "Our current offers are on the Deals page. Want me to show you sarees on deal?";
            return new Reply(msg, List.of(), List.of("Today's deals", "Under ₹3,000"), f, false, "offer", "/?view=deals#products", "Today's deals", "rules");
        }
        return null;
    }

    private Reply info(String msg, Filters f, String intent, String link, String label, boolean contact) {
        return new Reply(msg, List.of(), List.of("Wedding sarees", "Silk sarees", "Under ₹3,000", "Talk to us"), f, contact, intent, link, label, "rules");
    }

    // ------------------------------------------------------------------ understanding

    public static Filters parse(String t) {
        BigDecimal min = null, max = null;
        Matcher m = RANGE.matcher(t);
        boolean ranged = false;
        while (m.find()) {
            BigDecimal a = amount(m.group(1), m.group(2) != null || m.group(4) != null);
            BigDecimal b = amount(m.group(3), m.group(4) != null);
            // A real range: both look like prices and the second is bigger.
            if (a != null && b != null && b.compareTo(a) > 0 && a.compareTo(BigDecimal.valueOf(100)) >= 0) {
                min = a; max = b; ranged = true; break;
            }
        }
        if (!ranged) {
            Matcher u = UNDER.matcher(t);
            if (u.find()) max = amount(u.group(1), u.group(2) != null);
            Matcher o = OVER.matcher(t);
            if (o.find()) min = amount(o.group(1), o.group(2) != null);
            if (max == null && min == null) {
                Matcher b = BARE_PRICE.matcher(t);
                if (b.find()) max = b.group(1) != null ? amount(b.group(1), b.group(2) != null) : amount(b.group(3), true);
            }
        }
        if (max != null && max.compareTo(BigDecimal.valueOf(100)) < 0) max = null;
        if (min != null && min.compareTo(BigDecimal.valueOf(100)) < 0) min = null;

        String occasion = firstGroup(t, OCCASIONS);
        String fabric = firstGroup(t, FABRICS);
        String colour = null;
        for (String c : COLOURS) if (word(t, c) && (colour == null || c.length() > colour.length())) colour = c;
        Boolean deals = has(t, "deal", "deals", "sale", "discount", "offer", "cheap", "lowest price") ? Boolean.TRUE : null;
        return new Filters(min, max, occasion, fabric, colour, deals);
    }

    /** New words replace old ones; anything not mentioned is kept, so follow-ups refine the search. */
    static Filters merge(Filters a, Filters b) {
        return new Filters(
                b.minPrice() != null ? b.minPrice() : (b.maxPrice() != null && a.minPrice() != null && a.minPrice().compareTo(b.maxPrice()) >= 0 ? null : a.minPrice()),
                b.maxPrice() != null ? b.maxPrice() : (b.minPrice() != null && a.maxPrice() != null && a.maxPrice().compareTo(b.minPrice()) <= 0 ? null : a.maxPrice()),
                b.occasion() != null ? b.occasion() : a.occasion(),
                b.fabric() != null ? b.fabric() : a.fabric(),
                b.colour() != null ? b.colour() : a.colour(),
                b.dealsOnly() != null ? b.dealsOnly() : a.dealsOnly());
    }

    static List<Product> filter(List<Product> live, Filters f) {
        List<Product> out = new ArrayList<>();
        for (Product p : live) {
            BigDecimal price = effectivePrice(p);
            if (f.maxPrice() != null && (price == null || price.compareTo(f.maxPrice()) > 0)) continue;
            if (f.minPrice() != null && (price == null || price.compareTo(f.minPrice()) < 0)) continue;
            if (Boolean.TRUE.equals(f.dealsOnly()) && !Boolean.TRUE.equals(p.getDeal())) continue;
            if (f.occasion() != null && !matchesAny(text(p.getOccasion(), p.getName(), p.getDescription()), OCCASIONS.getOrDefault(f.occasion(), List.of(f.occasion())))) continue;
            if (f.fabric() != null && !matchesAny(text(p.getFabric(), p.getWeave(), p.getName(), p.getCategory() == null ? null : p.getCategory().getName()), FABRICS.getOrDefault(f.fabric(), List.of(f.fabric())))) continue;
            if (f.colour() != null && !matchesAny(text(p.getColour(), p.getName()), List.of(f.colour()))) continue;
            out.add(p);
        }
        return out;
    }

    // ------------------------------------------------------------------ wording

    static String describe(Filters f) {
        StringBuilder sb = new StringBuilder();
        if (f.colour() != null) sb.append(f.colour()).append(' ');
        if (f.fabric() != null) sb.append(f.fabric()).append(' ');
        sb.append(Boolean.TRUE.equals(f.dealsOnly()) ? "sarees on deal" : "sarees");
        if (f.occasion() != null) sb.append(" for ").append(switch (f.occasion()) {
            case "wedding" -> "a wedding";
            case "festive" -> "festivals";
            case "party" -> "a party";
            case "office" -> "the office";
            default -> "everyday wear";
        });
        if (f.minPrice() != null && f.maxPrice() != null) sb.append(" between ").append(money(f.minPrice())).append(" and ").append(money(f.maxPrice()));
        else if (f.maxPrice() != null) sb.append(" under ").append(money(f.maxPrice()));
        else if (f.minPrice() != null) sb.append(" above ").append(money(f.minPrice()));
        return sb.toString().trim();
    }

    private List<String> suggestions(Filters f, List<Product> found) {
        Set<String> out = new LinkedHashSet<>();
        if (f.maxPrice() == null) { out.add("Under ₹3,000"); out.add("Under ₹5,000"); }
        else if (f.maxPrice().compareTo(BigDecimal.valueOf(3000)) > 0) out.add("Under ₹3,000");
        if (f.occasion() == null) { out.add("For a wedding"); out.add("For office"); }
        if (f.colour() == null) {
            for (Product p : found) {
                if (p.getColour() != null && !p.getColour().isBlank() && out.size() < 6) out.add("In " + p.getColour().trim().toLowerCase(Locale.ROOT));
            }
        }
        if (f.fabric() == null) out.add("Only silk");
        if (!Boolean.TRUE.equals(f.dealsOnly())) out.add("On deal");
        out.add("Start over");
        return out.stream().limit(6).toList();
    }

    private ProductCard card(Product p) {
        List<String> why = new ArrayList<>();
        if (p.getFabric() != null) why.add(p.getFabric());
        if (p.getOccasion() != null) why.add(p.getOccasion());
        if (p.getColour() != null) why.add(p.getColour());
        if (Boolean.TRUE.equals(p.getDeal())) why.add("on deal");
        if (stock(p) > 0 && stock(p) <= 3) why.add("only " + stock(p) + " left");
        if (stock(p) == 0) why.add("sold out: ask to be notified");
        return new ProductCard(p.getId(), p.getName(), p.getImageUrl(), p.getPrice(),
                Boolean.TRUE.equals(p.getDeal()) ? p.getDealPrice() : null, Boolean.TRUE.equals(p.getDeal()),
                stock(p), stock(p) > 0, String.join(" · ", why));
    }

    private List<ProductCard> bestsellers() {
        return products.findPublic().stream().filter(p -> stock(p) > 0)
                .sorted(Comparator.comparing((Product p) -> -(p.getRating() == null ? 0 : p.getRating())))
                .limit(MAX_RESULTS).map(this::card).toList();
    }

    private String offerCode() {
        if (welcomeCode == null || welcomeCode.isBlank()) return null;
        return coupons.publicOffer(welcomeCode).map(c -> c.getCode()).orElse(null);
    }

    private static String query(Filters f) {
        List<String> q = new ArrayList<>();
        if (f.maxPrice() != null) {
            int max = f.maxPrice().intValue();
            q.add("price=" + (max <= 2000 ? "u2" : max <= 5000 ? "2to5" : max <= 10000 ? "5to10" : "o10"));
        }
        if (Boolean.TRUE.equals(f.dealsOnly())) q.add("view=deals");
        String term = f.fabric() != null ? f.fabric() : f.colour();
        if (term != null) q.add("q=" + term.replace(' ', '+'));
        return String.join("&", q);
    }

    // ------------------------------------------------------------------ helpers

    static BigDecimal effectivePrice(Product p) {
        return Boolean.TRUE.equals(p.getDeal()) && p.getDealPrice() != null ? p.getDealPrice() : p.getPrice();
    }

    private static int stock(Product p) { return p.getStockQuantity() == null ? 0 : p.getStockQuantity(); }

    private static BigDecimal amount(String digits, boolean thousands) {
        if (digits == null) return null;
        try {
            BigDecimal v = new BigDecimal(digits.replace(",", ""));
            return thousands ? v.multiply(BigDecimal.valueOf(1000)) : v;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static String money(BigDecimal v) {
        NumberFormat nf = NumberFormat.getIntegerInstance(new Locale("en", "IN"));
        return "₹" + nf.format(v.setScale(0, RoundingMode.HALF_UP));
    }

    private static String firstGroup(String t, Map<String, List<String>> groups) {
        for (Map.Entry<String, List<String>> e : groups.entrySet()) {
            for (String w : e.getValue()) if (word(t, w)) return e.getKey();
        }
        return null;
    }

    private static boolean matchesAny(String haystack, List<String> words) {
        for (String w : words) if (word(haystack, w)) return true;
        return false;
    }

    static boolean has(String t, String... phrases) {
        for (String p : phrases) if (word(t, p)) return true;
        return false;
    }

    private static boolean word(String t, String w) {
        return Pattern.compile("(?<![a-z])" + Pattern.quote(w) + "(?:s|es)?(?![a-z])").matcher(t).find();
    }

    private static String text(String... parts) {
        StringBuilder sb = new StringBuilder(" ");
        for (String p : parts) if (p != null) sb.append(p.toLowerCase(Locale.ROOT)).append(' ');
        return sb.toString();
    }
}
