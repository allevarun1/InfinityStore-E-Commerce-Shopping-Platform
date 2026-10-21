package com.example.ecommerce.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.ecommerce.model.AutomationEvent;
import com.example.ecommerce.model.AutomationSetting;
import com.example.ecommerce.model.CartItem;
import com.example.ecommerce.model.ERole;
import com.example.ecommerce.model.Lead;
import com.example.ecommerce.model.Order;
import com.example.ecommerce.model.OrderItem;
import com.example.ecommerce.model.Product;
import com.example.ecommerce.model.StockRequest;
import com.example.ecommerce.model.User;
import com.example.ecommerce.repository.AutomationEventRepository;
import com.example.ecommerce.repository.AutomationSettingRepository;
import com.example.ecommerce.repository.CartItemRepository;
import com.example.ecommerce.repository.LeadRepository;
import com.example.ecommerce.repository.OrderRepository;
import com.example.ecommerce.repository.ProductRepository;
import com.example.ecommerce.repository.StockRequestRepository;

/**
 * Autopilot: the routine follow-up work of a small shop, done on a schedule. Each rule is OFF, PREVIEW
 * (it writes the message and waits for the owner to send or skip it) or AUTO (it sends the email itself).
 * Every message is logged with its full text, and a subject key stops the same person being messaged
 * twice for the same reason. It also turns the store's records into a short to-do list for the owner.
 */
@Service
public class AutopilotService {
    private static final Logger log = LoggerFactory.getLogger(AutopilotService.class);

    public static final String OFF = "OFF", PREVIEW = "PREVIEW", AUTO = "AUTO";
    public static final Set<String> MODES = Set.of(OFF, PREVIEW, AUTO);
    public static final String BACK_IN_STOCK = "BACK_IN_STOCK", ABANDONED_CART = "ABANDONED_CART",
            REVIEW_REQUEST = "REVIEW_REQUEST", DAILY_DIGEST = "DAILY_DIGEST";

    public record Rule(String key, String name, String description, String defaultMode, boolean customerFacing) {}

    /** Customer-facing reminders start in PREVIEW so nothing reaches a shopper before the owner has seen one. */
    public static final List<Rule> RULES = List.of(
            new Rule(BACK_IN_STOCK, "Back-in-stock alerts",
                    "Emails everyone who asked to be told when a sold-out saree is back, as soon as it is in stock again.", AUTO, true),
            new Rule(ABANDONED_CART, "Abandoned bag reminders",
                    "Reminds signed-in shoppers about sarees left in their bag for more than 3 hours (once a week at most).", PREVIEW, true),
            new Rule(REVIEW_REQUEST, "Review requests",
                    "Thanks customers 2 days after delivery and asks them to review what they bought (once per order).", PREVIEW, true),
            new Rule(DAILY_DIGEST, "Owner's morning digest",
                    "Emails you a short summary each morning: sales, orders to act on, hot leads and stock to reorder.", AUTO, false));

    public record RuleView(String key, String name, String description, String mode, boolean customerFacing) {}

    public record Task(String key, String priority, String title, String detail, String actionLabel, String tab) {}

    public record Stats(int abandonedBags, BigDecimal abandonedValue, int waitingForStock, int hotLeads, int openLeads,
                        long sentThisWeek, long draftsWaiting) {}

    public record Overview(LocalDateTime serverTime, boolean scheduled, boolean mailConfigured, List<RuleView> rules, List<Task> tasks, Stats stats, List<AutomationEvent> events) {}

    public record RunSummary(int sent, int drafted, int noMail, Map<String, Integer> byRule) {}

    @Autowired private AutomationSettingRepository settings;
    @Autowired private AutomationEventRepository events;
    @Autowired private StockRequestRepository stockRequests;
    @Autowired private ProductRepository products;
    @Autowired private CartItemRepository carts;
    @Autowired private OrderRepository orders;
    @Autowired private LeadRepository leads;
    @Autowired private LeadService leadService;
    @Autowired private InsightsService insights;
    @Autowired private OpportunityService scout;
    @Autowired private CouponService coupons;
    @Autowired private MailService mail;
    @Autowired private AuditService audit;

    @Value("${app.frontend-url:}") private String frontendUrl;
    @Value("${app.cors.allowed-origins:}") private String corsOrigins;
    @Value("${app.automation.digest-to:${app.seed.admin.email:}}") private String digestTo;
    @Value("${app.automation.zone:Asia/Kolkata}") private String zone;
    @Value("${app.automation.digest-hour:8}") private int digestHour;
    @Value("${app.automation.cart-delay-hours:3}") private int cartDelayHours;
    @Value("${app.growth.welcome-code:WELCOME10}") private String welcomeCode;
    @Value("${app.store.name:Rekha Shopping Mall}") private String storeName;
    @Value("${app.automation.enabled:false}") private boolean scheduled;

    // ------------------------------------------------------------------ settings

    @Transactional(readOnly = true)
    public String mode(String key) {
        Rule rule = rule(key);
        return settings.findById(key).map(AutomationSetting::getMode).orElse(rule.defaultMode());
    }

    @Transactional
    public RuleView setMode(String key, String mode) {
        Rule rule = rule(key);
        String m = mode == null ? "" : mode.trim().toUpperCase();
        if (!MODES.contains(m)) throw new BusinessException("Mode must be OFF, PREVIEW or AUTO");
        AutomationSetting s = settings.findById(key).orElseGet(() -> new AutomationSetting(key, m));
        String before = settings.existsById(key) ? s.getMode() : rule.defaultMode();
        s.setMode(m);
        settings.save(s);
        audit.record("AUTOPILOT_MODE", "Automation", null, key + ": " + before + " -> " + m);
        return new RuleView(rule.key(), rule.name(), rule.description(), m, rule.customerFacing());
    }

    private static Rule rule(String key) {
        return RULES.stream().filter(r -> r.key().equals(key)).findFirst()
                .orElseThrow(() -> new BusinessException("Unknown automation " + key));
    }

    // ------------------------------------------------------------------ running

    /** Runs every rule once. Called by the scheduler and by the owner's "Run now" button. */
    @Transactional
    public synchronized RunSummary runAll() {
        int[] totals = new int[3]; // sent, drafted, noMail
        Map<String, Integer> byRule = new LinkedHashMap<>();
        for (Rule r : RULES) {
            String m = mode(r.key());
            if (OFF.equals(m)) { byRule.put(r.key(), 0); continue; }
            List<AutomationEvent> made = switch (r.key()) {
                case BACK_IN_STOCK -> backInStock(m);
                case ABANDONED_CART -> abandonedCarts(m);
                case REVIEW_REQUEST -> reviewRequests(m);
                case DAILY_DIGEST -> digest(m);
                default -> List.of();
            };
            for (AutomationEvent e : made) {
                switch (e.getStatus()) {
                    case AutomationEvent.SENT -> totals[0]++;
                    case AutomationEvent.DRAFT -> totals[1]++;
                    default -> totals[2]++;
                }
            }
            byRule.put(r.key(), made.size());
        }
        if (totals[0] + totals[1] + totals[2] > 0) {
            log.info("Autopilot run: {} sent, {} drafted, {} without email", totals[0], totals[1], totals[2]);
        }
        return new RunSummary(totals[0], totals[1], totals[2], byRule);
    }

    List<AutomationEvent> backInStock(String mode) {
        List<AutomationEvent> made = new ArrayList<>();
        Map<Long, Product> cache = new HashMap<>();
        for (StockRequest req : stockRequests.findByNotifiedAtIsNull()) {
            Product p = cache.computeIfAbsent(req.getProductId(), id -> products.findPublicById(id).orElse(null));
            if (p == null || p.getStockQuantity() == null || p.getStockQuantity() <= 0) continue;
            Lead lead = req.getLead();
            String key = "stock:" + req.getId();
            if (events.existsByRuleKeyAndSubjectKey(BACK_IN_STOCK, key)) { req.setNotifiedAt(LocalDateTime.now()); continue; }
            AutomationEvent e = new AutomationEvent(BACK_IN_STOCK, key, "Back in stock: " + p.getName() + " for " + who(lead.getName(), lead.getEmail(), lead.getPhone()));
            e.setRecipient(lead.getEmail());
            e.setRecipientName(lead.getName());
            e.setPhone(lead.getPhone());
            e.setEmailSubject("It's back: " + p.getName());
            e.setBody(greeting(lead.getName()) + "Good news! \"" + p.getName() + "\" is back in stock at " + storeName + ".\n\n"
                    + (p.getStockQuantity() <= 5 ? "Only " + p.getStockQuantity() + " available right now, so it may go quickly:\n" : "Here it is:\n")
                    + link("/products/" + p.getId()) + "\n\n"
                    + "You're receiving this because you asked us to tell you when it was back. We won't email you about it again.\n\n"
                    + "- " + storeName);
            // Phone-only requests have no email to send, so they always become a WhatsApp draft for the owner.
            deliver(e, lead.getEmail() == null ? PREVIEW : mode);
            req.setNotifiedAt(LocalDateTime.now());
            made.add(e);
        }
        return made;
    }

    List<AutomationEvent> abandonedCarts(String mode) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime quietSince = now.minusHours(cartDelayHours);
        Map<Long, List<CartItem>> byUser = new LinkedHashMap<>();
        for (CartItem item : carts.findByUpdatedAtAfterOrderByUpdatedAtAsc(now.minusDays(7))) {
            byUser.computeIfAbsent(item.getUser().getId(), k -> new ArrayList<>()).add(item);
        }
        List<AutomationEvent> made = new ArrayList<>();
        for (List<CartItem> bag : byUser.values()) {
            User user = bag.get(0).getUser();
            LocalDateTime last = bag.stream().map(CartItem::getUpdatedAt).max(Comparator.naturalOrder()).orElse(now);
            if (last.isAfter(quietSince) || isAdmin(user)) continue;
            String key = "cart:user:" + user.getId();
            if (events.existsByRuleKeyAndSubjectKeyAndCreatedAtAfter(ABANDONED_CART, key, now.minusDays(7))) continue;
            if (orders.existsByUserAndOrderDateAfter(user, last)) continue;
            List<CartItem> live = bag.stream().filter(i -> i.getProduct() != null && !Boolean.TRUE.equals(i.getProduct().getDeleted())
                    && i.getProduct().getStockQuantity() != null && i.getProduct().getStockQuantity() > 0).toList();
            if (live.isEmpty()) continue;

            BigDecimal value = bagValue(live);
            StringBuilder lines = new StringBuilder();
            for (CartItem i : live) {
                lines.append("  - ").append(i.getProduct().getName()).append(" x").append(i.getQuantity());
                int stock = i.getProduct().getStockQuantity();
                if (stock <= 3) lines.append(" (only ").append(stock).append(" left)");
                lines.append('\n');
            }
            String offer = "";
            String code = activeWelcomeCode();
            if (code != null && !orders.existsByUser(user)) {
                offer = "As it's your first order, code " + code + " still works at checkout.\n\n";
            }
            AutomationEvent e = new AutomationEvent(ABANDONED_CART, key, "Bag reminder for " + who(user.getFirstName(), user.getEmail(), null)
                    + " (" + live.size() + " item" + (live.size() == 1 ? "" : "s") + ", Rs. " + plain(value) + ")");
            e.setRecipient(user.getEmail());
            e.setRecipientName(user.getFirstName());
            e.setPhone(LeadService.normalisePhone(user.getPhone()));
            e.setEmailSubject("Still thinking it over? Your sarees are waiting");
            e.setBody(greeting(user.getFirstName()) + "You left these in your bag at " + storeName + ":\n\n" + lines + "\n"
                    + "We've kept them for you, but stock is limited. Pick up where you left off:\n" + link("/cart") + "\n\n"
                    + offer + "Cash on delivery is available. Questions? Just reply to this email.\n\n- " + storeName);
            deliver(e, mode);
            made.add(e);
        }
        return made;
    }

    List<AutomationEvent> reviewRequests(String mode) {
        LocalDateTime now = LocalDateTime.now();
        List<AutomationEvent> made = new ArrayList<>();
        for (Order o : orders.findByStatus("DELIVERED")) {
            LocalDateTime delivered = o.getStatusChangedAt() != null ? o.getStatusChangedAt() : o.getOrderDate();
            if (delivered == null || delivered.isAfter(now.minusDays(2)) || delivered.isBefore(now.minusDays(30))) continue;
            String key = "review:order:" + o.getId();
            if (events.existsByRuleKeyAndSubjectKey(REVIEW_REQUEST, key)) continue;
            User user = o.getUser();
            StringBuilder lines = new StringBuilder();
            for (OrderItem it : o.getOrderItems()) {
                String name = it.getProductName() != null ? it.getProductName() : it.getProduct().getName();
                lines.append("  - ").append(name).append(": ").append(link("/products/" + it.getProduct().getId() + "#reviews")).append('\n');
            }
            AutomationEvent e = new AutomationEvent(REVIEW_REQUEST, key, "Review request for order " + o.getTrackingNumber());
            e.setRecipient(user.getEmail());
            e.setRecipientName(user.getFirstName());
            e.setPhone(LeadService.normalisePhone(o.getPhoneNumber()));
            e.setEmailSubject("How do you like your saree?");
            e.setBody(greeting(user.getFirstName()) + "Thank you for shopping with " + storeName + "! We hope you love your order "
                    + o.getTrackingNumber() + ".\n\nWould you take a minute to tell other shoppers what you think? It helps a small shop like ours enormously:\n\n"
                    + lines + "\nIf anything isn't right, reply to this email and we'll make it right.\n\n- " + storeName);
            deliver(e, mode);
            made.add(e);
        }
        return made;
    }

    List<AutomationEvent> digest(String mode) {
        ZonedDateTime local = ZonedDateTime.now(zoneId());
        if (local.getHour() < digestHour) return List.of();
        return digestFor(local.toLocalDate(), mode);
    }

    @Transactional
    public List<AutomationEvent> digestFor(LocalDate day, String mode) {
        String key = "digest:" + day;
        if (events.existsByRuleKeyAndSubjectKey(DAILY_DIGEST, key) || digestTo == null || digestTo.isBlank()) return List.of();
        InsightsService.Dashboard week = insights.dashboard(7);
        Stats stats = stats();
        StringBuilder b = new StringBuilder("Good morning! Here's " + storeName + " today.\n\n");
        b.append("LAST 7 DAYS\n  Orders: ").append(week.orders()).append("\n  Net sales: Rs. ").append(plain(week.netSales()))
         .append("\n  Average order: Rs. ").append(plain(week.averageOrderValue())).append("\n\n");
        b.append("WAITING ON YOU\n");
        List<Task> tasks = tasks();
        if (tasks.isEmpty()) b.append("  Nothing urgent. A good day to add new sarees.\n");
        for (Task t : tasks) b.append("  [").append(t.priority()).append("] ").append(t.title()).append(" - ").append(t.detail()).append('\n');
        b.append("\nMONEY ON THE TABLE\n  ").append(stats.abandonedBags()).append(" bag").append(stats.abandonedBags() == 1 ? "" : "s")
         .append(" left behind (Rs. ").append(plain(stats.abandonedValue())).append(")\n  ")
         .append(stats.waitingForStock()).append(" shopper").append(stats.waitingForStock() == 1 ? "" : "s").append(" waiting for sold-out sarees\n  ")
         .append(stats.hotLeads()).append(" hot lead").append(stats.hotLeads() == 1 ? "" : "s").append(" to call\n\n");
        b.append("Open Autopilot: ").append(link("/admin?tab=autopilot")).append("\n");
        AutomationEvent e = new AutomationEvent(DAILY_DIGEST, key, "Morning digest for " + day);
        e.setRecipient(digestTo.trim());
        e.setRecipientName("Owner");
        e.setEmailSubject(storeName + " today: " + tasks.size() + " thing" + (tasks.size() == 1 ? "" : "s") + " to do");
        e.setBody(b.toString());
        deliver(e, mode);
        return List.of(e);
    }

    /** Sends (AUTO with mail), records NO_MAIL (AUTO without mail) or keeps a DRAFT (PREVIEW). */
    private void deliver(AutomationEvent e, String mode) {
        if (AUTO.equals(mode) && e.getRecipient() != null) {
            if (mail.configured()) {
                mail.send(e.getRecipient(), e.getEmailSubject(), e.getBody());
                e.setStatus(AutomationEvent.SENT);
                e.setHandledAt(LocalDateTime.now());
            } else {
                e.setStatus(AutomationEvent.NO_MAIL);
            }
        } else {
            e.setStatus(AutomationEvent.DRAFT);
        }
        events.save(e);
    }

    // ------------------------------------------------------------------ owner actions on a message

    @Transactional
    public AutomationEvent send(Long id) {
        AutomationEvent e = events.findById(id).orElseThrow(() -> new BusinessException("Message not found"));
        if (AutomationEvent.SENT.equals(e.getStatus())) throw new BusinessException("This message was already sent.");
        if (e.getRecipient() == null) throw new BusinessException("This person has no email address. Use the WhatsApp button instead.");
        if (!mail.configured()) throw new BusinessException("Email is not set up yet. Add MAIL_USERNAME and MAIL_PASSWORD, or copy the text into WhatsApp.");
        mail.send(e.getRecipient(), e.getEmailSubject(), e.getBody());
        e.setStatus(AutomationEvent.SENT);
        e.setHandledAt(LocalDateTime.now());
        audit.record("AUTOPILOT_SEND", "Automation", id, e.getRuleKey());
        return events.save(e);
    }

    /** Marks a WhatsApp follow-up as done, or skips a draft the owner does not want to send. */
    @Transactional
    public AutomationEvent close(Long id, boolean sentManually) {
        AutomationEvent e = events.findById(id).orElseThrow(() -> new BusinessException("Message not found"));
        if (AutomationEvent.SENT.equals(e.getStatus())) throw new BusinessException("This message was already sent.");
        e.setStatus(sentManually ? AutomationEvent.SENT : AutomationEvent.DISMISSED);
        e.setHandledAt(LocalDateTime.now());
        return events.save(e);
    }

    // ------------------------------------------------------------------ owner's overview

    @Transactional(readOnly = true)
    public Overview overview() {
        List<RuleView> rules = RULES.stream()
                .map(r -> new RuleView(r.key(), r.name(), r.description(), mode(r.key()), r.customerFacing())).toList();
        return new Overview(LocalDateTime.now(), scheduled, mail.configured(), rules, tasks(), stats(), events.findTop100ByOrderByCreatedAtDesc());
    }

    @Transactional(readOnly = true)
    public Stats stats() {
        LocalDateTime now = LocalDateTime.now();
        Map<Long, List<CartItem>> byUser = new HashMap<>();
        for (CartItem item : carts.findByUpdatedAtAfterOrderByUpdatedAtAsc(now.minusDays(7))) {
            if (!isAdmin(item.getUser())) byUser.computeIfAbsent(item.getUser().getId(), k -> new ArrayList<>()).add(item);
        }
        int bags = 0;
        BigDecimal value = BigDecimal.ZERO;
        for (List<CartItem> bag : byUser.values()) {
            LocalDateTime last = bag.stream().map(CartItem::getUpdatedAt).max(Comparator.naturalOrder()).orElse(now);
            if (last.isAfter(now.minusHours(cartDelayHours)) || orders.existsByUserAndOrderDateAfter(bag.get(0).getUser(), last)) continue;
            bags++;
            value = value.add(bagValue(bag));
        }
        int waiting = 0;
        for (Object[] row : stockRequests.countWaitingByProduct()) waiting += ((Number) row[1]).intValue();
        int hot = 0, open = 0;
        for (LeadService.LeadView l : leadService.list()) {
            if (!LeadService.isOpen(l.status())) continue;
            open++;
            if ("hot".equals(l.temperature())) hot++;
        }
        return new Stats(bags, value, waiting, hot, open,
                events.countByStatusAndCreatedAtAfter(AutomationEvent.SENT, now.minusDays(7)),
                events.countByStatus(AutomationEvent.DRAFT) + events.countByStatus(AutomationEvent.NO_MAIL));
    }

    /** The owner's to-do list, most urgent first. Each item is a plain count from the store's records. */
    @Transactional(readOnly = true)
    public List<Task> tasks() {
        LocalDateTime now = LocalDateTime.now();
        List<Task> out = new ArrayList<>();

        List<Order> pending = orders.findByStatus("PENDING");
        if (!pending.isEmpty()) {
            LocalDateTime oldest = pending.stream().map(Order::getOrderDate).min(Comparator.naturalOrder()).orElse(now);
            long hours = Duration.between(oldest, now).toHours();
            out.add(new Task("confirm-orders", hours >= 24 ? "HIGH" : "MEDIUM",
                    "Confirm " + pending.size() + " new order" + (pending.size() == 1 ? "" : "s"),
                    "Oldest is " + age(hours) + " old. Call to confirm cash-on-delivery orders before shipping.", "Open orders", "orders"));
        }
        long toShip = orders.findByStatus("CONFIRMED").stream().filter(o -> {
            LocalDateTime since = o.getStatusChangedAt() != null ? o.getStatusChangedAt() : o.getOrderDate();
            return since != null && since.isBefore(now.minusDays(1));
        }).count();
        if (toShip > 0) {
            out.add(new Task("ship-orders", "HIGH", "Ship " + toShip + " confirmed order" + (toShip == 1 ? "" : "s"),
                    "Confirmed more than a day ago and not yet marked shipped.", "Open orders", "orders"));
        }

        List<LeadService.LeadView> fresh = leadService.list().stream()
                .filter(l -> Lead.NEW.equals(l.status()) && ("hot".equals(l.temperature()) || "warm".equals(l.temperature()))).toList();
        if (!fresh.isEmpty()) {
            long hot = fresh.stream().filter(l -> "hot".equals(l.temperature())).count();
            out.add(new Task("call-leads", hot > 0 ? "HIGH" : "MEDIUM",
                    "Follow up " + fresh.size() + " interested shopper" + (fresh.size() == 1 ? "" : "s"),
                    (hot > 0 ? hot + " hot. " : "") + "Each has a ready-to-send WhatsApp message in Leads.", "Open leads", "leads"));
        }

        Map<Long, Integer> waiting = new HashMap<>();
        for (Object[] row : stockRequests.countWaitingByProduct()) waiting.put((Long) row[0], ((Number) row[1]).intValue());
        waiting.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).limit(3).forEach(e -> products.findById(e.getKey()).ifPresent(p -> {
            if (p.getStockQuantity() == null || p.getStockQuantity() <= 0) {
                out.add(new Task("restock-" + p.getId(), e.getValue() >= 3 ? "HIGH" : "MEDIUM",
                        e.getValue() + " shopper" + (e.getValue() == 1 ? " is" : "s are") + " waiting for " + p.getName(),
                        "It is sold out. Restock it and Autopilot will tell them straight away.", "Open products", "products"));
            }
        }));

        long drafts = events.countByStatus(AutomationEvent.DRAFT) + events.countByStatus(AutomationEvent.NO_MAIL);
        if (drafts > 0) {
            out.add(new Task("review-drafts", "MEDIUM", "Review " + drafts + " message" + (drafts == 1 ? "" : "s") + " Autopilot prepared",
                    "Send, WhatsApp or skip each one.", "Open Autopilot", "autopilot"));
        }

        for (OpportunityService.Opportunity o : scout.scout().opportunities()) {
            if (!"HIGH".equals(o.severity()) || "OUT_OF_STOCK_DEMAND".equals(o.type()) && waiting.containsKey(o.productId())) continue;
            out.add(new Task("opp-" + o.type() + "-" + o.productId(), "MEDIUM", o.title(), o.evidence() + " " + o.suggestedAction(), "Open insights", "insights"));
            if (out.size() >= 9) break;
        }

        if (!mail.configured()) {
            out.add(new Task("setup-mail", "LOW", "Turn on email so Autopilot can send",
                    "Set MAIL_USERNAME and MAIL_PASSWORD on the server. Until then messages wait here for you to copy.", "How to", "autopilot"));
        }
        out.sort(Comparator.comparingInt(t -> switch (t.priority()) { case "HIGH" -> 0; case "MEDIUM" -> 1; default -> 2; }));
        return out;
    }

    // ------------------------------------------------------------------ helpers

    private String activeWelcomeCode() {
        if (welcomeCode == null || welcomeCode.isBlank()) return null;
        return coupons.publicOffer(welcomeCode).map(c -> c.getCode()).orElse(null);
    }

    private static BigDecimal bagValue(List<CartItem> bag) {
        BigDecimal v = BigDecimal.ZERO;
        for (CartItem i : bag) {
            Product p = i.getProduct();
            BigDecimal unit = Boolean.TRUE.equals(p.getDeal()) && p.getDealPrice() != null ? p.getDealPrice() : p.getPrice();
            if (unit != null) v = v.add(unit.multiply(BigDecimal.valueOf(i.getQuantity())));
        }
        return v;
    }

    private static boolean isAdmin(User u) {
        return u.getRoles().stream().anyMatch(r -> r.getName() == ERole.ROLE_ADMIN);
    }

    private String link(String path) {
        String base = frontendUrl != null && !frontendUrl.isBlank() ? frontendUrl
                : corsOrigins == null || corsOrigins.isBlank() ? "" : corsOrigins.split(",")[0].trim();
        if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        return base + path;
    }

    private ZoneId zoneId() {
        try { return ZoneId.of(zone); } catch (Exception e) { return ZoneId.of("Asia/Kolkata"); }
    }

    private static String greeting(String name) {
        return "Hi" + (name == null || name.isBlank() ? "" : " " + name.trim().split(" ")[0]) + ",\n\n";
    }

    private static String who(String name, String email, String phone) {
        if (name != null && !name.isBlank()) return name.trim();
        if (email != null) return email;
        return phone == null ? "a shopper" : "+91 " + phone;
    }

    private static String age(long hours) {
        return hours < 1 ? "under an hour" : hours < 48 ? hours + " hour" + (hours == 1 ? "" : "s") : (hours / 24) + " days";
    }

    private static String plain(BigDecimal v) {
        return v == null ? "0" : v.setScale(0, RoundingMode.HALF_UP).toPlainString();
    }
}
