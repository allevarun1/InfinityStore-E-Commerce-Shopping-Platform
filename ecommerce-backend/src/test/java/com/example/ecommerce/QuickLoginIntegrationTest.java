package com.example.ecommerce;

import com.example.ecommerce.model.User;
import com.example.ecommerce.security.GoogleIdTokenVerifier;
import com.example.ecommerce.security.GoogleIdTokenVerifier.GoogleUser;
import com.example.ecommerce.service.BusinessException;
import com.example.ecommerce.service.TruecallerLoginService;
import com.example.ecommerce.service.WhatsAppOtpService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Continue with Google, WhatsApp OTP and Truecaller one-tap, end to end on our side (Google/Meta/Truecaller mocked). */
@org.springframework.test.context.TestPropertySource(properties = "app.truecaller.app-key=test-truecaller-key")
class QuickLoginIntegrationTest extends IntegrationTestSupport {
    private static final AtomicInteger N = new AtomicInteger(500);

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired TruecallerLoginService truecaller;
    @MockBean GoogleIdTokenVerifier google;
    @MockBean WhatsAppOtpService whatsapp;

    private JsonNode body(ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private ResultActions googleLogin(GoogleUser user) throws Exception {
        when(google.verify("cred-" + user.subject())).thenReturn(user);
        return mvc.perform(post("/api/auth/google").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("credential", "cred-" + user.subject()))));
    }

    @Test
    void googleCreatesACustomerOnceAndStaysSignedInFor30Days() throws Exception {
        int n = N.incrementAndGet();
        GoogleUser g = new GoogleUser("sub-" + n, "shopper" + n + "@gmail.com", "Asha", "Rao");
        JsonNode first = body(googleLogin(g).andExpect(status().isOk()));
        assertEquals("ROLE_CUSTOMER", first.get("roles").get(0).asText());
        assertEquals("Asha", first.get("firstName").asText());
        assertEquals("shopper" + n + "@gmail.com", first.get("email").asText());
        JsonNode again = body(googleLogin(g).andExpect(status().isOk()));
        assertEquals(first.get("id").asLong(), again.get("id").asLong());

        String[] parts = first.get("token").asText().split("\\.");
        JsonNode claims = json.readTree(java.util.Base64.getUrlDecoder().decode(parts[1]));
        long days = (claims.get("exp").asLong() - claims.get("iat").asLong()) / 86400;
        assertEquals(30, days, "customers are not asked to log in again for a month");
    }

    @Test
    void googleLinksToTheCustomerWhoAlreadyUsesThatEmail() throws Exception {
        User existing = customer();
        JsonNode login = body(googleLogin(new GoogleUser("sub-link-" + existing.getId(), existing.getEmail(), "X", "Y"))
                .andExpect(status().isOk()));
        assertEquals(existing.getId(), login.get("id").asLong(), "orders and addresses stay on one account");
        assertNotNull(users.findById(existing.getId()).orElseThrow().getGoogleSubject());
    }

    @Test
    void googleNeverOpensTheAdminAccount() throws Exception {
        User admin = users.findByUsername("admin").orElseThrow();
        googleLogin(new GoogleUser("sub-admin", admin.getEmail(), "A", "B")).andExpect(status().isBadRequest());
        when(google.verify("bad")).thenThrow(new BusinessException("Google sign-in did not complete. Please try again."));
        mvc.perform(post("/api/auth/google").contentType(MediaType.APPLICATION_JSON).content("{\"credential\":\"bad\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void whatsappOtpSignsInByTheVerifiedNumber() throws Exception {
        String mobile = "98" + String.format("%08d", N.incrementAndGet());
        mvc.perform(post("/api/auth/otp/send").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("phone", mobile)))).andExpect(status().isNoContent());
        verify(whatsapp).send(mobile);

        when(whatsapp.verify(mobile, "111111")).thenReturn(false);
        mvc.perform(post("/api/auth/otp/verify").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("phone", mobile, "code", "111111")))).andExpect(status().isBadRequest());

        when(whatsapp.verify(mobile, "123456")).thenReturn(true);
        JsonNode session = body(mvc.perform(post("/api/auth/otp/verify").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("phone", mobile, "code", "123456")))).andExpect(status().isOk()));
        User user = users.findByVerifiedPhone("+91" + mobile).orElseThrow();
        assertEquals(user.getId(), session.get("id").asLong());
        assertEquals(mobile, user.getPhone(), "checkout gets the number already filled");
    }

    @Test
    void truecallerHandsTheSessionToTheBrowserThatStartedIt() throws Exception {
        String nonce = body(mvc.perform(post("/api/auth/truecaller/start")).andExpect(status().isOk())).get("nonce").asText();
        assertEquals("PENDING", body(mvc.perform(get("/api/auth/truecaller/status/" + nonce)).andExpect(status().isOk())).get("status").asText());

        String phone = "+9197" + String.format("%08d", N.incrementAndGet());
        truecaller.markVerified(nonce, new TruecallerLoginService.Verified(phone, "Meena", "Kumari", null));
        JsonNode done = body(mvc.perform(get("/api/auth/truecaller/status/" + nonce)).andExpect(status().isOk()));
        assertEquals("VERIFIED", done.get("status").asText());
        assertEquals("Meena", done.get("session").get("firstName").asText());
        assertEquals(phone, users.findById(done.get("session").get("id").asLong()).orElseThrow().getVerifiedPhone());

        mvc.perform(get("/api/auth/truecaller/status/" + nonce)).andExpect(status().isBadRequest());
        // Truecaller's server-to-server callback is public and always answered 200, even when it is junk.
        mvc.perform(post("/api/auth/truecaller/callback").contentType(MediaType.APPLICATION_JSON).content("{\"requestId\":\"nope\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void storeInfoTellsTheSiteWhichButtonsToShow() throws Exception {
        when(google.clientId()).thenReturn("client-1.apps.googleusercontent.com");
        when(whatsapp.enabled()).thenReturn(true);
        JsonNode login = body(mvc.perform(get("/api/store/info")).andExpect(status().isOk())).get("login");
        assertEquals("client-1.apps.googleusercontent.com", login.get("googleClientId").asText());
        assertTrue(login.get("whatsappOtp").asBoolean());
        verify(whatsapp, never()).send(anyString());
        verify(google, never()).verify(eq("x"));
    }
}
