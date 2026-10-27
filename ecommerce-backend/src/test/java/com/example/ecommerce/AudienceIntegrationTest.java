package com.example.ecommerce;

import com.example.ecommerce.model.Lead;
import com.example.ecommerce.model.Product;
import com.example.ecommerce.model.StockRequest;
import com.example.ecommerce.model.User;
import com.example.ecommerce.model.WishlistItem;
import com.example.ecommerce.repository.LeadRepository;
import com.example.ecommerce.repository.StockRequestRepository;
import com.example.ecommerce.repository.WishlistItemRepository;
import com.example.ecommerce.service.OrderService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** "Who should I send this saree to?" and the customer list behind the owner's WhatsApp tools. */
class AudienceIntegrationTest extends IntegrationTestSupport {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired LeadRepository leads;
    @Autowired StockRequestRepository stockRequests;
    @Autowired WishlistItemRepository wishlist;
    @Autowired OrderService orderService;

    @AfterEach
    void clean() {
        stockRequests.deleteAll();
        wishlist.deleteAll();
        leads.deleteAll();
    }

    private String adminToken() throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/signin").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("username", "admin", "password", "test-admin-password")))).andReturn();
        return json.readTree(r.getResponse().getContentAsString(StandardCharsets.UTF_8)).get("token").asText();
    }

    private Lead lead(String name, String phone, Long productId, String status) {
        Lead l = new Lead(name.toLowerCase().replace(' ', '.') + "@test.local", "enquiry");
        l.setName(name);
        l.setPhone(phone);
        l.setProductId(productId);
        if (status != null) l.setStatus(status);
        return leads.save(l);
    }

    private JsonNode audience(String t, Product p) throws Exception {
        MvcResult r = mvc.perform(get("/api/admin/share/audience").param("productId", p.getId().toString()).header("Authorization", "Bearer " + t))
                .andExpect(status().isOk()).andReturn();
        return json.readTree(r.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    @Test
    void ranksThePeopleMostLikelyToBuyAndLeavesOutThoseWhoSaidNo() throws Exception {
        Product saree = product("4999", 5);
        Product other = product("2999", 5);

        User saver = customer();
        saver.setPhone("+91 98450 11111");                    // messy formatting is cleaned to 10 digits
        users.save(saver);
        wishlist.save(new WishlistItem(saver, saree));

        Lead waiting = lead("Asha", "09845022222", null, null);
        stockRequests.save(new StockRequest(waiting, saree.getId()));
        lead("Meena", "9845033333", saree.getId(), null);
        lead("Said No", "9845044444", saree.getId(), Lead.LOST);
        lead("No Phone", null, saree.getId(), null);
        lead("Landline", "0802345678", saree.getId(), null);   // not a mobile: WhatsApp cannot reach it

        User pastBuyer = customer();                            // bought from the same category: a softer signal
        pastBuyer.setPhone("9845066666");                       // profile phone differs from the checkout phone below
        users.save(pastBuyer);
        addToCart(pastBuyer, other, 1);
        var r = request("COD");
        r.setPhoneNumber("9845077777");
        orderService.placeOrder(pastBuyer, r, null);

        User bought = customer();                               // already owns this saree: not suggested again
        addToCart(bought, saree, 1);
        orderService.placeOrder(bought, request("COD"), null);

        String t = adminToken();
        mvc.perform(get("/api/admin/share/audience").param("productId", saree.getId().toString())).andExpect(status().isUnauthorized());
        JsonNode people = audience(t, saree);
        List<String> phones = new ArrayList<>();
        people.forEach(p -> phones.add(p.get("phone").asText()));

        assertEquals("9845011111", people.get(0).get("phone").asText(), "the person who saved it comes first: " + phones);
        assertTrue(people.get(0).get("reasons").toString().contains("Saved this saree"));
        assertEquals("9845022222", people.get(1).get("phone").asText(), "then the one waiting for it to be back");
        assertEquals("9845033333", people.get(2).get("phone").asText(), "then the one who asked about it");
        assertFalse(phones.contains("9845044444"), "a lead marked lost is never suggested");
        assertTrue(phones.contains("9845077777"), "a past buyer of the same category is suggested, at their latest checkout phone");
        assertFalse(phones.contains("9845066666"), "and appears once, not again under their profile phone: " + phones);
        assertFalse(phones.contains("0802345678"), "only mobile numbers");
        // both test orders used the same checkout phone; that person already bought this saree, so is left out
        assertFalse(phones.contains("9876543210"), "people who already bought it are left out");
    }

    @Test
    void customerListShowsWhatEachCustomerIsWorth() throws Exception {
        Product saree = product("3999", 5);
        User buyer = customer();
        addToCart(buyer, saree, 2);
        orderService.placeOrder(buyer, request("COD"), null);
        wishlist.save(new WishlistItem(buyer, product("1999", 5)));

        MvcResult r = mvc.perform(get("/api/admin/customers").header("Authorization", "Bearer " + adminToken())).andExpect(status().isOk()).andReturn();
        JsonNode list = json.readTree(r.getResponse().getContentAsString(StandardCharsets.UTF_8));
        JsonNode me = null, admin = null;
        for (JsonNode c : list) {
            if (c.get("username").asText().equals(buyer.getUsername())) me = c;
            if (c.get("username").asText().equals("admin")) admin = c;
        }
        assertNotNull(me);
        assertEquals(1, me.get("orders").asInt());
        assertTrue(me.get("spent").asDouble() > 7000, "two sarees plus GST: " + me.get("spent"));
        assertEquals("9876543210", me.get("phone").asText(), "phone comes from checkout when the profile has none");
        assertEquals(1, me.get("saved").asInt());
        assertTrue(admin.get("admin").asBoolean(), "staff accounts are flagged so the list can keep them apart");
    }
}
