package com.example.ecommerce;

import com.example.ecommerce.model.User;
import com.example.ecommerce.security.FirebasePhoneVerifier;
import com.example.ecommerce.service.BusinessException;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Login with mobile number + OTP, and the address book checkout fills from. */
class PhoneLoginIntegrationTest extends IntegrationTestSupport {
    private static final AtomicInteger N = new AtomicInteger(100);

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockBean FirebasePhoneVerifier verifier;

    private String newNumber() {
        return "+9198765" + String.format("%05d", N.incrementAndGet());
    }

    private JsonNode body(ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private JsonNode phoneLogin(String number) throws Exception {
        when(verifier.verify("otp-token-" + number)).thenReturn(number);
        return body(mvc.perform(post("/api/auth/phone").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("idToken", "otp-token-" + number))))
                .andExpect(status().isOk()));
    }

    private Map<String, String> address(String line1) {
        return Map.of("fullName", "Lakshmi Devi", "phone", "9876543210", "line1", line1, "line2", "Near temple",
                "city", "Hyderabad", "state", "Telangana", "pincode", "500001");
    }

    @Test
    void firstOtpLoginCreatesACustomerAndTheNextOneOpensTheSameAccount() throws Exception {
        String number = newNumber();
        JsonNode first = phoneLogin(number);
        assertTrue(first.get("token").asText().length() > 20);
        assertEquals("ROLE_CUSTOMER", first.get("roles").get(0).asText());
        assertTrue(first.get("email").isNull(), "no email is invented for OTP customers");

        User created = users.findByVerifiedPhone(number).orElseThrow();
        assertEquals(number.substring(3), created.getPhone(), "checkout gets the 10-digit number");

        JsonNode second = phoneLogin(number);
        assertEquals(first.get("id").asLong(), second.get("id").asLong());
    }

    @Test
    void aProfilePhoneNeverLetsSomeoneElseIntoAnAccount() throws Exception {
        String number = newNumber();
        User existing = customer();
        existing.setPhone(number.substring(3)); // typed on a profile, never verified
        users.save(existing);

        JsonNode login = phoneLogin(number);
        assertNotEquals(existing.getId(), login.get("id").asLong());
    }

    @Test
    void anInvalidOrExpiredOtpIsRejected() throws Exception {
        when(verifier.verify(anyString())).thenThrow(new BusinessException("Your OTP has expired. Please request a new one."));
        mvc.perform(post("/api/auth/phone").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("idToken", "bad"))))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/phone").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void addressBookSavesOnceFillsTheNameAndIsPrivate() throws Exception {
        String token = "Bearer " + phoneLogin(newNumber()).get("token").asText();
        String body = json.writeValueAsString(address("Flat 4, Sri Sai Residency"));

        long id = body(mvc.perform(post("/api/user/addresses").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk())).get("id").asLong();
        // The same address again (different case) updates the saved one rather than adding a copy.
        mvc.perform(post("/api/user/addresses").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(address("FLAT 4, SRI SAI RESIDENCY")))).andExpect(status().isOk());

        JsonNode list = body(mvc.perform(get("/api/user/addresses").header("Authorization", token)).andExpect(status().isOk()));
        assertEquals(1, list.size());
        assertEquals(id, list.get(0).get("id").asLong());
        assertEquals("500001", list.get(0).get("pincode").asText());

        JsonNode profile = body(mvc.perform(get("/api/user/profile").header("Authorization", token)).andExpect(status().isOk()));
        assertEquals("Lakshmi", profile.get("firstName").asText());
        assertEquals("Devi", profile.get("lastName").asText());
        assertTrue(profile.get("mobileLogin").asBoolean());

        String other = "Bearer " + phoneLogin(newNumber()).get("token").asText();
        mvc.perform(delete("/api/user/addresses/" + id).header("Authorization", other)).andExpect(status().isBadRequest());
        assertEquals(0, body(mvc.perform(get("/api/user/addresses").header("Authorization", other))).size());
        mvc.perform(delete("/api/user/addresses/" + id).header("Authorization", token)).andExpect(status().isNoContent());
    }

    @Test
    void invalidAddressesAreRejected() throws Exception {
        String token = "Bearer " + phoneLogin(newNumber()).get("token").asText();
        Map<String, String> bad = new java.util.HashMap<>(address("Flat 4, Sri Sai Residency"));
        bad.put("pincode", "12345");
        mvc.perform(post("/api/user/addresses").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(bad))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/user/addresses")).andExpect(status().isUnauthorized());
    }

    @Test
    void otpCustomerCanAddAnEmailWithoutAPassword() throws Exception {
        String token = "Bearer " + phoneLogin(newNumber()).get("token").asText();
        String email = "otp" + N.incrementAndGet() + "@test.local";
        mvc.perform(put("/api/user/profile").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("firstName", "Asha", "email", email)))).andExpect(status().isOk());
        // Changing it again now needs the password, like any other account.
        mvc.perform(put("/api/user/profile").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("firstName", "Asha", "email", "x" + email)))).andExpect(status().isBadRequest());
    }
}
