package com.example.ecommerce;

import com.example.ecommerce.model.Order;
import com.example.ecommerce.model.Product;
import com.example.ecommerce.model.User;
import com.example.ecommerce.service.OrderService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthSecurityIntegrationTest extends IntegrationTestSupport {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired OrderService orderService;
    @Value("${app.seed.admin.password}") String adminPassword;

    private String body(Map<String, ?> m) throws Exception {
        return json.writeValueAsString(m);
    }

    private String signIn(String username, String password) throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/signin").contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("username", username, "password", password)))).andReturn();
        assertEquals(200, r.getResponse().getStatus(), "sign-in should succeed for " + username);
        return json.readTree(r.getResponse().getContentAsString()).get("token").asText();
    }

    private String resetTokenSentTo(String email) {
        ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);
        verify(mail).sendPasswordReset(eq(email), link.capture(), anyLong());
        String l = link.getValue();
        return l.substring(l.indexOf("token=") + 6);
    }

    // ---- password recovery ----

    @Test
    void forgotPasswordResponseIsIdenticalForKnownAndUnknownEmails() throws Exception {
        User u = customer();
        MvcResult known = mvc.perform(post("/api/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("email", u.getEmail())))).andReturn();
        MvcResult unknown = mvc.perform(post("/api/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("email", "nobody@test.local")))).andReturn();

        assertEquals(200, known.getResponse().getStatus());
        assertEquals(known.getResponse().getStatus(), unknown.getResponse().getStatus());
        assertEquals(known.getResponse().getContentAsString(), unknown.getResponse().getContentAsString());
        verify(mail, never()).sendPasswordReset(eq("nobody@test.local"), org.mockito.ArgumentMatchers.anyString(), anyLong());
    }

    @Test
    void resetTokenIsHashedAtRestAndWorksExactlyOnce() throws Exception {
        User u = customer();
        mvc.perform(post("/api/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("email", u.getEmail())))).andExpect(status().isOk());
        String token = resetTokenSentTo(u.getEmail());

        User stored = users.findById(u.getId()).orElseThrow();
        assertNotEquals(token, stored.getResetTokenHash(), "the raw token must not be stored");
        assertEquals(64, stored.getResetTokenHash().length());
        assertNull(stored.getResetToken(), "the legacy plaintext column stays empty");
        assertTrue(token.length() >= 43, "256-bit token");

        Map<String, String> reset = Map.of("email", u.getEmail(), "code", token,
                "newPassword", "brand-new-pass", "confirmPassword", "brand-new-pass");
        mvc.perform(post("/api/auth/reset-password").contentType(MediaType.APPLICATION_JSON).content(body(reset)))
                .andExpect(status().isOk());
        signIn(u.getUsername(), "brand-new-pass");

        mvc.perform(post("/api/auth/reset-password").contentType(MediaType.APPLICATION_JSON).content(body(reset)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void expiredTokenIsRejected() throws Exception {
        User u = customer();
        mvc.perform(post("/api/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("email", u.getEmail())))).andExpect(status().isOk());
        String token = resetTokenSentTo(u.getEmail());

        User stored = users.findById(u.getId()).orElseThrow();
        stored.setResetTokenExpiresAt(LocalDateTime.now().minusMinutes(1));
        users.save(stored);

        mvc.perform(post("/api/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("email", u.getEmail(), "code", token,
                        "newPassword", "another-pass-1", "confirmPassword", "another-pass-1"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void tokenIsInvalidatedAfterTooManyWrongGuesses() throws Exception {
        User u = customer();
        mvc.perform(post("/api/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("email", u.getEmail())))).andExpect(status().isOk());
        String token = resetTokenSentTo(u.getEmail());

        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                    .content(body(Map.of("email", u.getEmail(), "code", "guess-" + i,
                            "newPassword", "another-pass-1", "confirmPassword", "another-pass-1"))))
                    .andExpect(status().isBadRequest());
        }
        // Even the correct token no longer works.
        mvc.perform(post("/api/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("email", u.getEmail(), "code", token,
                        "newPassword", "another-pass-1", "confirmPassword", "another-pass-1"))))
                .andExpect(status().isBadRequest());
    }

    // ---- response data exposure ----

    @Test
    void adminUserListNeverExposesSecrets() throws Exception {
        User u = customer();
        mvc.perform(post("/api/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("email", u.getEmail())))).andExpect(status().isOk());
        String admin = signIn("admin", adminPassword);

        String res = mvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(res.contains(u.getUsername()));
        assertFalse(res.toLowerCase().contains("reset"), "no reset fields in user JSON");
        assertFalse(res.toLowerCase().contains("password"), "no password fields in user JSON");
        assertFalse(res.contains("$2a$"), "no bcrypt hashes");
    }

    @Test
    void orderResponsesDoNotEmbedUserRecords() throws Exception {
        User u = customer();
        Product p = product("100.00", 5);
        addToCart(u, p, 1);
        Order o = orderService.placeOrder(u, request("COD"), null);

        String customerToken = signIn(u.getUsername(), "password-" + u.getUsername().substring(4));
        String mine = mvc.perform(get("/api/orders/history").header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertFalse(mine.toLowerCase().contains("reset"));
        assertFalse(mine.toLowerCase().contains("password"));
        assertFalse(mine.contains(u.getEmail()), "customer order view carries no account record");
        assertTrue(mine.contains(o.getTrackingNumber()));

        String admin = signIn("admin", adminPassword);
        String all = mvc.perform(get("/api/admin/orders").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode first = json.readTree(all).get(0);
        assertTrue(first.has("user"));
        assertFalse(first.get("user").has("phone"));
        assertFalse(first.get("user").has("addresses"));
        assertFalse(all.toLowerCase().contains("reset"));
    }

    // ---- authorization ----

    @Test
    void customerCannotReadAnotherCustomersOrderOrUseAdminEndpoints() throws Exception {
        User owner = customer();
        User other = customer();
        Product p = product("100.00", 5);
        addToCart(owner, p, 1);
        Order o = orderService.placeOrder(owner, request("COD"), null);
        String otherToken = signIn(other.getUsername(), "password-" + other.getUsername().substring(4));

        mvc.perform(get("/api/orders/" + o.getId()).header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/orders/" + o.getId() + "/status").header("Authorization", "Bearer " + otherToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"SHIPPED\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/orders/" + o.getId())).andExpect(status().isUnauthorized());
    }

    @Test
    void adminCannotDeleteSelfOrAccountsWithOrders() throws Exception {
        String admin = signIn("admin", adminPassword);
        Long adminId = users.findByUsername("admin").orElseThrow().getId();
        mvc.perform(delete("/api/admin/users/" + adminId).header("Authorization", "Bearer " + admin))
                .andExpect(status().isBadRequest());

        User buyer = customer();
        Product p = product("100.00", 5);
        addToCart(buyer, p, 1);
        orderService.placeOrder(buyer, request("COD"), null);
        mvc.perform(delete("/api/admin/users/" + buyer.getId()).header("Authorization", "Bearer " + admin))
                .andExpect(status().isBadRequest());
        assertTrue(users.existsById(buyer.getId()));

        User plain = customer();
        mvc.perform(delete("/api/admin/users/" + plain.getId()).header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk());
        assertFalse(users.existsById(plain.getId()));
        assertFalse(audit.findByEntityTypeAndEntityIdOrderByCreatedAtAsc("USER", plain.getId()).isEmpty());
    }

    @Test
    void changingPasswordRequiresTheCurrentPassword() throws Exception {
        User u = customer();
        String token = signIn(u.getUsername(), "password-" + u.getUsername().substring(4));

        mvc.perform(put("/api/user/profile").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("firstName", "A", "lastName", "B", "password", "a-new-password"))))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/user/profile").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("firstName", "A", "lastName", "B", "password", "a-new-password",
                        "currentPassword", "password-" + u.getUsername().substring(4)))))
                .andExpect(status().isOk());
    }

    @Test
    void storeInfoIsPublicAndMatchesPricingConfiguration() throws Exception {
        String res = mvc.perform(get("/api/store/info")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode info = json.readTree(res);
        assertEquals("INR", info.get("currency").asText());
        assertEquals(999, info.get("freeShippingThreshold").asInt());
        assertEquals(49, info.get("shippingFee").asInt());
        assertTrue(info.get("cashOnDelivery").asBoolean());
        assertFalse(info.get("onlinePayment").asBoolean());
    }

    // ---- abuse protection and error hygiene ----

    @Test
    void accountIsLockedAfterRepeatedFailedSignIns() throws Exception {
        User u = customer();
        for (int i = 0; i < 8; i++) {
            mvc.perform(post("/api/auth/signin").contentType(MediaType.APPLICATION_JSON)
                    .content(body(Map.of("username", u.getUsername(), "password", "wrong-" + i))))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/auth/signin").contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("username", u.getUsername(), "password", "password-" + u.getUsername().substring(4)))))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void errorsDoNotLeakInternals() throws Exception {
        String res = mvc.perform(post("/api/auth/signin").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();
        assertFalse(res.contains("Exception"));
        assertFalse(res.contains("com.example"));
    }
}
