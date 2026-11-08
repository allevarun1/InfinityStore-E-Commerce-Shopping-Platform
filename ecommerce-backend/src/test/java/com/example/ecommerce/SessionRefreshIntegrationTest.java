package com.example.ecommerce;

import com.example.ecommerce.model.ERole;
import com.example.ecommerce.model.User;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Base64;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Signed-in browsers renew their session instead of being sent back to the login page. */
class SessionRefreshIntegrationTest extends IntegrationTestSupport {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Value("${app.seed.admin.password}") String adminPassword;

    private static final double DAY_S = 24 * 60 * 60;

    private String signIn(String username, String password) throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/signin").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("username", username, "password", password)))).andReturn();
        assertEquals(200, r.getResponse().getStatus());
        return json.readTree(r.getResponse().getContentAsString()).get("token").asText();
    }

    private double daysLeft(String jwt) throws Exception {
        JsonNode claims = json.readTree(Base64.getUrlDecoder().decode(jwt.split("[.]")[1]));
        return (claims.get("exp").asLong() - System.currentTimeMillis() / 1000.0) / DAY_S;
    }

    private MvcResult refresh(String token) throws Exception {
        var req = post("/api/auth/refresh");
        if (token != null) req.header("Authorization", "Bearer " + token);
        return mvc.perform(req).andReturn();
    }

    @Test
    void customerGetsAFresh30DaySession() throws Exception {
        User u = new User("renew1", "renew1@test.local", encoder.encode("password-renew1"), "Re", "New");
        u.setRoles(Set.of(roles.findByName(ERole.ROLE_CUSTOMER).orElseThrow()));
        users.save(u);
        String first = signIn("renew1", "password-renew1");
        assertTrue(daysLeft(first) > 29 && daysLeft(first) <= 30);

        MvcResult r = refresh(first);
        assertEquals(200, r.getResponse().getStatus());
        JsonNode body = json.readTree(r.getResponse().getContentAsString());
        assertEquals("renew1", body.get("username").asText());
        String renewed = body.get("token").asText();
        assertTrue(daysLeft(renewed) > 29, "the renewed token must again last about 30 days");
        // And the renewed token really works.
        assertEquals(200, refresh(renewed).getResponse().getStatus());
    }

    @Test
    void adminStaysSignedInForAYearAndRenews() throws Exception {
        String token = signIn("admin", adminPassword);
        assertTrue(daysLeft(token) > 364 && daysLeft(token) <= 365, "admin login should last a year");
        MvcResult r = refresh(token);
        assertEquals(200, r.getResponse().getStatus());
        assertTrue(daysLeft(json.readTree(r.getResponse().getContentAsString()).get("token").asText()) > 364);
    }

    @Test
    void noToken_orABadToken_isRefusedSoTheShopperSignsInAgain() throws Exception {
        assertEquals(401, refresh(null).getResponse().getStatus());
        assertEquals(401, refresh("not.a.token").getResponse().getStatus());
    }
}
