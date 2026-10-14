package com.example.ecommerce;

import com.example.ecommerce.model.*;
import com.example.ecommerce.service.InsightsService;
import com.example.ecommerce.service.OpportunityService;
import com.example.ecommerce.service.OrderService;
import com.example.ecommerce.service.studio.AnthropicListingWriter;
import com.example.ecommerce.service.studio.ListingWriter;
import com.example.ecommerce.service.studio.StudioRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

class InsightsStudioIntegrationTest extends IntegrationTestSupport {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired OrderService orderService;
    @Autowired InsightsService insights;
    @Autowired OpportunityService scout;
    @Autowired com.example.ecommerce.repository.WishlistItemRepository wishlists;
    @Value("${app.seed.admin.password}") String adminPassword;

    private String token(String username, String password) throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/signin").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("username", username, "password", password)))).andReturn();
        assertEquals(200, r.getResponse().getStatus());
        return json.readTree(r.getResponse().getContentAsString()).get("token").asText();
    }

    private Product costed(String price, String cost, int stock) {
        Product p = product(price, stock);
        p.setCostPrice(new BigDecimal(cost));
        return products.save(p);
    }

    private InsightsService.ProductPerformance row(Long id) {
        return insights.dashboard(30).topProducts().stream().filter(t -> t.productId().equals(id)).findFirst().orElse(null);
    }

    // ---- dashboard ----

    @Test
    void profitUsesTheCostRecordedAtOrderTimeAndIgnoresCancelledOrders() {
        Product p = costed("99999.00", "60000.00", 20); // priced high so it ranks in the top five
        User a = customer(), b = customer(), c = customer();
        addToCart(a, p, 1);
        addToCart(b, p, 2);
        addToCart(c, p, 1);
        orderService.placeOrder(a, request("COD"), null);
        orderService.placeOrder(b, request("COD"), null);
        Order cancelled = orderService.placeOrder(c, request("COD"), null);
        orderService.updateOrderStatus(cancelled.getId(), "CANCELLED");

        // the supplier raises their price afterwards; past profit must not change
        p.setCostPrice(new BigDecimal("95000.00"));
        products.save(p);

        InsightsService.ProductPerformance r = row(p.getId());
        assertNotNull(r, "product appears in top sellers");
        assertEquals(3, r.units(), "cancelled order is not counted");
        assertEquals(0, new BigDecimal("299997.00").compareTo(r.netSales()));
        assertEquals(0, new BigDecimal("119997.00").compareTo(r.grossProfit())); // 3 x (99999 - 60000)
        assertEquals(40.0, r.marginPercent(), 0.1);
    }

    @Test
    void salesWithoutACostReportNoProfitInsteadOfInventingOne() {
        Product p = product("88888.00", 5);
        User u = customer();
        addToCart(u, p, 1);
        orderService.placeOrder(u, request("COD"), null);
        InsightsService.ProductPerformance r = row(p.getId());
        assertNotNull(r);
        assertNull(r.grossProfit());
        assertNull(r.marginPercent());
    }

    @Test
    void testPaymentRefundedAndCancelledOrdersAreNotSales() {
        Order o = new Order();
        o.setStatus("CONFIRMED");
        o.setPaymentStatus("PAID");
        assertTrue(InsightsService.counts(o));
        o.setTestPayment(true);
        assertFalse(InsightsService.counts(o));
        o.setTestPayment(false);
        o.setPaymentStatus("REFUNDED");
        assertFalse(InsightsService.counts(o));
        o.setPaymentStatus("PENDING");
        o.setStatus("CANCELLED");
        assertFalse(InsightsService.counts(o));
    }

    @Test
    void unitCostNeverReachesCustomersThroughTheirOrders() throws Exception {
        Product p = costed("700.00", "400.00", 5);
        User u = customer();
        addToCart(u, p, 1);
        Order o = orderService.placeOrder(u, request("COD"), null);
        String item = json.writeValueAsString(orders.findById(o.getId()).orElseThrow().getOrderItems().get(0));
        assertFalse(item.contains("unitCost"));
        assertFalse(item.contains("400"));
    }

    @Test
    void insightsAndStudioAreAdminOnly() throws Exception {
        User u = customer();
        u.setPassword(encoder.encode("shopper-pass-9"));
        users.save(u);
        String shopper = token(u.getUsername(), "shopper-pass-9");
        for (String path : new String[] {"/api/admin/insights/dashboard", "/api/admin/insights/opportunities", "/api/admin/studio/status"}) {
            assertEquals(401, mvc.perform(get(path)).andReturn().getResponse().getStatus(), path);
            assertEquals(403, mvc.perform(get(path).header("Authorization", "Bearer " + shopper)).andReturn().getResponse().getStatus(), path);
        }
        assertEquals(403, mvc.perform(post("/api/admin/studio/draft").contentType(MediaType.APPLICATION_JSON).content("{}")
                .header("Authorization", "Bearer " + shopper)).andReturn().getResponse().getStatus());
    }

    // ---- scout ----

    @Test
    void scoutFlagsFastSellersSoldOutDemandAndThinMargins() {
        Product fast = costed("77777.00", "50000.00", 5);
        User buyer = customer();
        addToCart(buyer, fast, 4);
        orderService.placeOrder(buyer, request("COD"), null); // 4 sold in the window, 1 left

        Product gone = product("1200.00", 0);
        wishlists.save(new WishlistItem(customer(), gone));
        wishlists.save(new WishlistItem(customer(), gone));

        Product thin = costed("1000.00", "900.00", 10);
        Product loss = costed("1000.00", "1200.00", 10);

        OpportunityService.Report report = scout.scout();
        assertTrue(has(report, "RESTOCK", fast.getId()), "fast seller with about a week of cover");
        assertTrue(has(report, "OUT_OF_STOCK_DEMAND", gone.getId()));
        assertTrue(has(report, "LOW_MARGIN", thin.getId()));
        OpportunityService.Opportunity lossFinding = report.opportunities().stream()
                .filter(o -> o.type().equals("LOW_MARGIN") && loss.getId().equals(o.productId())).findFirst().orElseThrow();
        assertEquals("HIGH", lossFinding.severity(), "selling below cost is urgent");
        assertTrue(report.opportunities().stream().filter(o -> o.productId() != null)
                .allMatch(o -> o.evidence() != null && !o.evidence().isBlank()), "every finding cites its evidence");
    }

    private boolean has(OpportunityService.Report r, String type, Long productId) {
        return r.opportunities().stream().anyMatch(o -> o.type().equals(type) && productId.equals(o.productId()));
    }

    // ---- studio ----

    private Long categoryId() {
        return categories.findAll().stream().findFirst().orElseGet(() -> categories.save(new Category("Test", "Test category"))).getId();
    }

    @Test
    void studioDraftUsesOnlySuppliedFactsAndSuggestsAPriceFromCost() throws Exception {
        String admin = token("admin", adminPassword);
        Map<String, Object> facts = Map.of("categoryId", categoryId(), "fabric", "Silk", "weave", "Banarasi", "colour", "Maroon",
                "occasion", "Wedding", "region", "Varanasi", "lengthMeters", 5.5, "blouseIncluded", true, "costPrice", 650);
        MvcResult r = mvc.perform(post("/api/admin/studio/draft").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(facts)).header("Authorization", "Bearer " + admin)).andReturn();
        assertEquals(200, r.getResponse().getStatus());
        JsonNode d = json.readTree(r.getResponse().getContentAsString());

        assertEquals("template", d.get("generatedBy").asText());
        assertFalse(d.get("sentToExternalService").asBoolean());
        assertEquals("Maroon Banarasi Silk Saree", d.get("name").asText());
        assertTrue(d.get("description").asText().startsWith("A maroon Banarasi silk saree from Varanasi."));
        String text = d.get("description").asText().toLowerCase();
        assertTrue(text.contains("varanasi") && text.contains("5.5 metres") && text.contains("blouse piece"));
        for (String invented : new String[] {"handwoven", "pure", "authentic", "handloom"}) {
            assertFalse(text.contains(invented), "must not invent '" + invented + "'");
        }
        assertEquals(0, new BigDecimal("999").compareTo(d.get("suggestedPrice").decimalValue())); // 650 / 0.65 = 1000, ending in 9
        assertFalse(d.has("warning") && !d.get("warning").isNull());
    }

    @Test
    void templateUsesTheRightArticleAndSuggestsMoreDetailWhenThin() throws Exception {
        String admin = token("admin", adminPassword);
        Map<String, Object> facts = Map.of("categoryId", categoryId(), "colour", "Emerald");
        JsonNode d = json.readTree(mvc.perform(post("/api/admin/studio/draft").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(facts)).header("Authorization", "Bearer " + admin)).andReturn().getResponse().getContentAsString());
        assertTrue(d.get("description").asText().startsWith("An emerald saree"));
        assertTrue(d.get("warning").asText().contains("short"));
    }

    @Test
    void studioWarnsWhenAWriterAddsClaimsTheOwnerNeverGave() {
        StudioRequest facts = new StudioRequest();
        facts.setFabric("Cotton");
        ListingWriter.ListingText text = new ListingWriter.ListingText("Pure handwoven Cotton Saree", "A saree.");
        var claims = com.example.ecommerce.service.studio.StudioServiceAccess.claims(text, facts);
        assertTrue(claims.contains("pure") && claims.contains("handwoven"));

        facts.setNotes("Handwoven by a cooperative in Chirala");
        assertFalse(com.example.ecommerce.service.studio.StudioServiceAccess.claims(text, facts).contains("handwoven"),
                "a claim the owner supplied is allowed");
    }

    @Test
    void aiWriterSendsOnlyTypedFactsNeverCostAndParsesReplies() throws Exception {
        AnthropicListingWriter writer = new AnthropicListingWriter("anthropic", "sk-test-key", "claude-sonnet-5-5", "http://localhost:1", json);
        assertTrue(writer.configured());
        assertFalse(new AnthropicListingWriter("none", "sk-test-key", "m", "http://localhost:1", json).configured());
        assertFalse(new AnthropicListingWriter("anthropic", "", "m", "http://localhost:1", json).configured());

        StudioRequest facts = new StudioRequest();
        facts.setFabric("Silk");
        facts.setColour("Green");
        facts.setCostPrice(new BigDecimal("4321"));
        facts.setNotes("Gift for a wedding");
        String body = json.writeValueAsString(com.example.ecommerce.service.studio.StudioServiceAccess.body(writer, "Silk Sarees", facts));
        assertTrue(body.contains("Green") && body.contains("Gift for a wedding"));
        assertFalse(body.contains("4321"), "cost is private and must never be sent to an AI provider");
        assertFalse(body.toLowerCase().contains("cost"));

        JsonNode fenced = json.readTree("{\"content\":[{\"type\":\"text\",\"text\":\"```json\\n{\\\"name\\\":\\\"Green Silk Saree\\\",\\\"description\\\":\\\"A green silk saree.\\\"}\\n```\"}]}");
        assertEquals("Green Silk Saree", com.example.ecommerce.service.studio.StudioServiceAccess.parse(writer, fenced).name());
        assertThrows(Exception.class, () -> com.example.ecommerce.service.studio.StudioServiceAccess.parse(writer, json.readTree("{\"content\":[]}")));
    }
}
