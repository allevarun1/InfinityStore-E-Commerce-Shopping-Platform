package com.example.ecommerce;

import com.example.ecommerce.model.*;
import com.example.ecommerce.repository.AutomationEventRepository;
import com.example.ecommerce.repository.LeadRepository;
import com.example.ecommerce.repository.StockRequestRepository;
import com.example.ecommerce.service.AutopilotService;
import com.example.ecommerce.service.LeadService;
import com.example.ecommerce.service.assistant.ShopAssistantService;
import com.example.ecommerce.service.assistant.ShopAssistantService.Filters;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Shopping assistant, lead capture and Autopilot. Mail is mocked: nothing is really sent. */
class GrowthEngineIntegrationTest extends IntegrationTestSupport {
    private static final AtomicInteger N = new AtomicInteger();

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired ShopAssistantService assistant;
    @Autowired LeadService leadService;
    @Autowired AutopilotService autopilot;
    @Autowired LeadRepository leads;
    @Autowired StockRequestRepository stockRequests;
    @Autowired AutomationEventRepository events;

    private Product saree(String name, String price, int stock, String fabric, String occasion, String colour) {
        Product p = product(price, stock);
        p.setName(name + " " + N.incrementAndGet());
        p.setFabric(fabric);
        p.setOccasion(occasion);
        p.setColour(colour);
        return products.save(p);
    }

    private JsonNode chat(String message, Object filters) throws Exception {
        MvcResult r = mvc.perform(post("/api/assistant/chat").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(filters == null ? Map.of("message", message) : Map.of("message", message, "filters", filters))))
                .andExpect(status().isOk()).andReturn();
        return json.readTree(r.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
    }

    private String token(String username, String password) throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/signin").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("username", username, "password", password)))).andReturn();
        return json.readTree(r.getResponse().getContentAsString()).get("token").asText();
    }

    // ------------------------------------------------------------------ assistant

    @Test
    void assistantUnderstandsBudgetOccasionFabricAndColourInPlainWords() {
        Filters f = ShopAssistantService.parse(" red kanjivaram saree for my sister's shaadi under 5k ");
        assertEquals(new BigDecimal("5000"), f.maxPrice());
        assertEquals("wedding", f.occasion());
        assertEquals("silk", f.fabric());
        assertEquals("red", f.colour());

        Filters range = ShopAssistantService.parse(" cotton between 1,500 and 3000 for office ");
        assertEquals(new BigDecimal("1500"), range.minPrice());
        assertEquals(new BigDecimal("3000"), range.maxPrice());
        assertEquals("office", range.occasion());

        // Small numbers are not budgets ("3 to 5 days").
        assertNull(ShopAssistantService.parse(" delivered in 3 to 5 days ").maxPrice());
    }

    @Test
    void assistantRecommendsOnlyLiveMatchingSareesWithinBudget() throws Exception {
        Product match = saree("Ruby Wedding Silk", "4500.00", 4, "Silk", "Wedding", "Red");
        Product tooDear = saree("Ruby Wedding Silk Grand", "9000.00", 4, "Silk", "Wedding", "Red");
        Product draft = saree("Ruby Wedding Silk Draft", "4000.00", 4, "Silk", "Wedding", "Red");
        draft.setStatus(ProductStatus.DRAFT);
        products.save(draft);

        JsonNode r = chat("show me red silk sarees for a wedding under 5000", null);
        assertEquals("recommend", r.get("intent").asText());
        List<Long> ids = new java.util.ArrayList<>();
        r.get("products").forEach(p -> ids.add(p.get("id").asLong()));
        assertTrue(ids.contains(match.getId()), "the matching saree is recommended");
        assertFalse(ids.contains(tooDear.getId()), "over-budget sarees are left out");
        assertFalse(ids.contains(draft.getId()), "unpublished sarees are never shown");
        r.get("products").forEach(p -> assertTrue(p.get("price").decimalValue().compareTo(new BigDecimal("5000")) <= 0
                || (p.hasNonNull("dealPrice") && p.get("dealPrice").decimalValue().compareTo(new BigDecimal("5000")) <= 0)));

        // A follow-up keeps what was said before and narrows the budget.
        JsonNode next = chat("under 3000 please", json.treeToValue(r.get("filters"), Map.class));
        assertEquals("wedding", next.get("filters").get("occasion").asText());
        assertEquals(0, new BigDecimal("3000").compareTo(next.get("filters").get("maxPrice").decimalValue()));

        JsonNode reset = chat("start over", json.treeToValue(next.get("filters"), Map.class));
        assertEquals("greeting", reset.get("intent").asText());
        assertTrue(reset.get("filters").get("occasion").isNull());
    }

    @Test
    void assistantSaysSoWhenNothingMatchesAndOffersToTakeContact() throws Exception {
        JsonNode r = chat("purple organza for a party above 99,999,999", null);
        assertTrue(r.get("offerContact").asBoolean());
        assertTrue(r.get("products").isEmpty());
    }

    @Test
    void assistantAnswersShopQuestionsFromRealSettings() throws Exception {
        assertEquals("payment", chat("is cash on delivery available?", null).get("intent").asText());
        assertEquals("size", chat("how long is the saree, is a blouse included?", null).get("intent").asText());
        JsonNode ship = chat("what are the delivery charges", null);
        assertEquals("shipping", ship.get("intent").asText());
        assertTrue(ship.get("reply").asText().contains("₹999"), ship.get("reply").asText());
        assertEquals("/track", chat("where is my order", null).get("link").asText());
        assertTrue(chat("can I talk to a person", null).get("offerContact").asBoolean());
    }

    // ------------------------------------------------------------------ leads

    @Test
    void phoneOnlyLeadIsCapturedAndARepeatCountsAsAnotherTouch() throws Exception {
        String phone = "98" + String.format("%08d", N.incrementAndGet());
        Map<String, Object> body = Map.of("name", "Lakshmi", "phone", "+91 " + phone, "interest", "green silk for Diwali", "source", "assistant_contact", "consent", true);
        mvc.perform(post("/api/store/leads").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))).andExpect(status().isOk());
        mvc.perform(post("/api/store/leads").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("phone", phone, "interest", "bridal red", "source", "assistant", "consent", true)))).andExpect(status().isOk());

        Lead lead = leads.findFirstByPhone(phone).orElseThrow();
        assertEquals(2, lead.getTouches());
        assertNull(lead.getEmail());
        assertEquals("assistant_contact", lead.getSource(), "the stronger intent stays the headline source");
        assertTrue(lead.getInterest().startsWith("bridal red"), lead.getInterest());

        LeadService.LeadView v = leadService.list().stream().filter(l -> phone.equals(l.phone())).findFirst().orElseThrow();
        assertEquals("hot", v.temperature(), v.reasons().toString());
        assertTrue(v.whatsappLink().startsWith("https://wa.me/91" + phone + "?text="));
        assertTrue(v.followUp().contains("Lakshmi"));
    }

    @Test
    void leadCaptureRejectsBadInputAndMissingConsent() throws Exception {
        mvc.perform(post("/api/store/leads").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("phone", "12345", "consent", true)))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/store/leads").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", "a@b.co", "consent", false)))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/store/leads").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("consent", true)))).andExpect(status().isBadRequest());
    }

    @Test
    void leadExportNeutralisesSpreadsheetFormulas() {
        leadService.capture(new LeadService.Capture("=HYPERLINK(\"x\")", "csv" + N.incrementAndGet() + "@test.local", null, null, null, "footer"));
        assertTrue(leadService.csv().contains("\"'=HYPERLINK(\"\"x\"\")\""));
    }

    @Test
    void leadAndAutopilotScreensAreAdminOnly() throws Exception {
        User c = customer();
        String t = token(c.getUsername(), "password-" + c.getUsername().substring(4));
        mvc.perform(get("/api/admin/leads").header("Authorization", "Bearer " + t)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/autopilot").header("Authorization", "Bearer " + t)).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/autopilot/run")).andExpect(status().isUnauthorized());
        String admin = token("admin", "test-admin-password");
        mvc.perform(get("/api/admin/autopilot").header("Authorization", "Bearer " + admin)).andExpect(status().isOk());
        mvc.perform(put("/api/admin/autopilot/rules/ABANDONED_CART").param("mode", "SOMETIMES").header("Authorization", "Bearer " + admin))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------ Autopilot

    @Test
    void backInStockAlertGoesOutOnceWhenTheSareeReturns() throws Exception {
        when(mail.configured()).thenReturn(true);
        Product p = saree("Peacock Paithani", "7000.00", 0, "Silk", "Festive", "Green");
        String email = "wait" + N.incrementAndGet() + "@test.local";
        mvc.perform(post("/api/store/leads").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", email, "productId", p.getId(), "source", "back_in_stock", "consent", true))))
                .andExpect(status().isOk());

        autopilot.runAll();
        verify(mail, never()).send(eq(email), anyString(), anyString());

        p.setStockQuantity(3);
        products.save(p);
        autopilot.runAll();
        autopilot.runAll();
        verify(mail, times(1)).send(eq(email), contains(p.getName()), contains("/products/" + p.getId()));
        Lead lead = leads.findFirstByEmailIgnoreCase(email).orElseThrow();
        assertNotNull(stockRequests.findByLeadIdAndProductId(lead.getId(), p.getId()).orElseThrow().getNotifiedAt());
    }

    @Test
    void abandonedBagBecomesADraftOnceAndTheOwnerCanSendIt() {
        when(mail.configured()).thenReturn(true);
        autopilot.setMode(AutopilotService.ABANDONED_CART, AutopilotService.PREVIEW);
        User u = customer();
        Product p = saree("Lotus Chanderi", "2400.00", 6, "Cotton", "Office", "Pink");
        CartItem line = new CartItem(u, p, 1);
        line.setUpdatedAt(LocalDateTime.now().minusHours(5));
        carts.save(line);

        autopilot.runAll();
        autopilot.runAll();
        List<AutomationEvent> mine = events.findAll().stream().filter(e -> ("cart:user:" + u.getId()).equals(e.getSubjectKey())).toList();
        assertEquals(1, mine.size(), "one reminder per bag per week");
        AutomationEvent draft = mine.get(0);
        assertEquals(AutomationEvent.DRAFT, draft.getStatus());
        assertTrue(draft.getBody().contains(p.getName()));
        verify(mail, never()).send(eq(u.getEmail()), anyString(), anyString());

        autopilot.send(draft.getId());
        verify(mail).send(eq(u.getEmail()), anyString(), contains(p.getName()));
        assertEquals(AutomationEvent.SENT, events.findById(draft.getId()).orElseThrow().getStatus());
    }

    @Test
    void noBagReminderForAFreshBagOrAfterTheShopperOrdered() {
        User fresh = customer();
        carts.save(new CartItem(fresh, saree("Fresh Bag", "1500.00", 5, "Cotton", null, null), 1));

        User bought = customer();
        CartItem old = new CartItem(bought, saree("Bought Bag", "1500.00", 5, "Cotton", null, null), 1);
        old.setUpdatedAt(LocalDateTime.now().minusHours(6));
        carts.save(old);
        Order o = new Order();
        o.setUser(bought);
        o.setOrderDate(LocalDateTime.now().minusHours(1));
        o.setStatus("PENDING");
        o.setTotalPrice(new BigDecimal("1500.00"));
        o.setShippingAddress("1 Test Road");
        orders.save(o);

        autopilot.runAll();
        assertFalse(events.existsByRuleKeyAndSubjectKey(AutopilotService.ABANDONED_CART, "cart:user:" + fresh.getId()));
        assertFalse(events.existsByRuleKeyAndSubjectKey(AutopilotService.ABANDONED_CART, "cart:user:" + bought.getId()));
    }

    @Test
    void deliveredOrderGetsOneReviewRequestAfterTwoDays() {
        User u = customer();
        Product p = saree("Review Me", "3000.00", 5, "Silk", null, null);
        Order o = new Order();
        o.setUser(u);
        o.setOrderDate(LocalDateTime.now().minusDays(6));
        o.setStatus("DELIVERED");
        o.setStatusChangedAt(LocalDateTime.now().minusDays(3));
        o.setTotalPrice(new BigDecimal("3000.00"));
        o.setShippingAddress("1 Test Road");
        o.setTrackingNumber("INF-TEST-" + N.incrementAndGet());
        OrderItem it = new OrderItem();
        it.setOrder(o);
        it.setProduct(p);
        it.setQuantity(1);
        it.setPrice(new BigDecimal("3000.00"));
        o.getOrderItems().add(it);
        orders.save(o);

        autopilot.runAll();
        autopilot.runAll();
        List<AutomationEvent> mine = events.findAll().stream().filter(e -> ("review:order:" + o.getId()).equals(e.getSubjectKey())).toList();
        assertEquals(1, mine.size());
        assertTrue(mine.get(0).getBody().contains("/products/" + p.getId()));
    }

    @Test
    void ownerTodoListPointsAtWaitingShoppersAndTheDigestStaysHonestWithoutMail() {
        Product p = saree("Waited For", "5000.00", 0, "Silk", null, null);
        for (int i = 0; i < 3; i++) {
            leadService.capture(new LeadService.Capture(null, "w" + N.incrementAndGet() + "@test.local", null, null, p.getId(), "back_in_stock"));
        }
        List<AutopilotService.Task> tasks = autopilot.tasks();
        assertTrue(tasks.stream().anyMatch(t -> t.title().contains("3 shoppers are waiting for " + p.getName()) && "HIGH".equals(t.priority())), tasks.toString());
        assertTrue(tasks.stream().anyMatch(t -> "setup-mail".equals(t.key())), "mail is not configured in this test");
        assertTrue(autopilot.stats().waitingForStock() >= 3);
    }

    @Test
    void morningDigestIsWrittenOnceAndMarkedUnsentWhenMailIsOff() {
        java.time.LocalDate day = java.time.LocalDate.of(2030, 1, N.incrementAndGet() % 28 + 1);
        List<AutomationEvent> first = autopilot.digestFor(day, AutopilotService.AUTO);
        assertEquals(1, first.size());
        assertEquals(AutomationEvent.NO_MAIL, first.get(0).getStatus());
        assertEquals("owner@test.local", first.get(0).getRecipient());
        assertTrue(first.get(0).getBody().contains("WAITING ON YOU"));
        assertTrue(autopilot.digestFor(day, AutopilotService.AUTO).isEmpty(), "one digest per day");
        verify(mail, never()).send(eq("owner@test.local"), anyString(), anyString());
    }
}
