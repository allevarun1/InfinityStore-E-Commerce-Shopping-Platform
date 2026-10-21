package com.example.ecommerce;

import com.example.ecommerce.repository.StoreVideoRepository;
import com.example.ecommerce.repository.TestimonialRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Featured testimonials, plus clips hosted on the storefront itself. */
class TestimonialIntegrationTest extends IntegrationTestSupport {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired TestimonialRepository repo;
    @Autowired StoreVideoRepository videos;

    @AfterEach
    void clean() { repo.deleteAll(); videos.deleteAll(); }

    private String adminToken() throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/signin").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("username", "admin", "password", "test-admin-password")))).andReturn();
        return json.readTree(r.getResponse().getContentAsString(StandardCharsets.UTF_8)).get("token").asText();
    }

    private ResultActions addQuote(String t, String name, String quote, int rating) throws Exception {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("name", name);
        b.put("location", "Pune");
        b.put("quote", quote);
        b.put("rating", rating);
        return mvc.perform(post("/api/admin/testimonials").header("Authorization", "Bearer " + t)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(b)));
    }

    @Test
    void ownerFeaturesQuotesAndShoppersSeeOnlyTheLiveOnes() throws Exception {
        mvc.perform(get("/api/admin/testimonials")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/admin/testimonials").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
        String t = adminToken();
        addQuote(t, "Divya", "Lovely fabric and the border is exactly as shown. Delivered earlier than I expected.", 5).andExpect(status().isOk());
        MvcResult r = addQuote(t, "Rhea", "Soft cotton, easy to drape, and the colour matches the photos nicely.", 4).andExpect(status().isOk()).andReturn();
        long id = json.readTree(r.getResponse().getContentAsString(StandardCharsets.UTF_8)).get("id").asLong();
        JsonNode pub = json.readTree(mvc.perform(get("/api/store/testimonials")).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertEquals(2, pub.size());
        assertFalse(pub.get(0).get("sample").asBoolean(), "owner-added quotes are never labelled as samples");
        mvc.perform(put("/api/admin/testimonials/" + id + "/active").param("value", "false").header("Authorization", "Bearer " + t)).andExpect(status().isOk());
        assertEquals(1, json.readTree(mvc.perform(get("/api/store/testimonials")).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8)).size());
        mvc.perform(delete("/api/admin/testimonials/" + id).header("Authorization", "Bearer " + t)).andExpect(status().isOk());
        assertEquals(1, repo.count());
    }

    @Test
    void badQuotesAreRefused() throws Exception {
        String t = adminToken();
        addQuote(t, "", "A perfectly fine quote that is long enough to pass.", 5).andExpect(status().isBadRequest());
        addQuote(t, "Sana", "Too short", 5).andExpect(status().isBadRequest());
        addQuote(t, "Sana", "x".repeat(301), 5).andExpect(status().isBadRequest());
        addQuote(t, "Sana", "A perfectly fine quote that is long enough to pass.", 0).andExpect(status().isBadRequest());
        addQuote(t, "Sana", "A perfectly fine quote that is long enough to pass.", 6).andExpect(status().isBadRequest());
        assertEquals(0, repo.count());
    }

    @Test
    void clipsHostedOnTheSiteAreAcceptedButNothingElseThatLooksLikeAPath() throws Exception {
        String t = adminToken();
        for (String ok : new String[]{"/videos/story.mp4", "/videos/reels/silk-1.webm"}) {
            mvc.perform(post("/api/admin/videos").header("Authorization", "Bearer " + t).contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(Map.of("title", "Site clip", "videoUrl", ok)))).andExpect(status().isOk());
        }
        for (String bad : new String[]{"/videos/../secret.mp4", "/images/a.mp4", "//evil.example.com/videos/a.mp4", "/videos/a.html", "/videos/a b.mp4", "videos/a.mp4"}) {
            mvc.perform(post("/api/admin/videos").header("Authorization", "Bearer " + t).contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(Map.of("title", "Bad", "videoUrl", bad)))).andExpect(status().isBadRequest());
        }
        assertEquals(2, videos.count());
    }
}
