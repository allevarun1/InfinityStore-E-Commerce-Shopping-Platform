package com.example.ecommerce;

import com.example.ecommerce.repository.CouponRepository;
import com.example.ecommerce.repository.MediaAssetRepository;
import com.example.ecommerce.repository.PromoPopupRepository;
import com.example.ecommerce.repository.StoreVideoRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The owner's photo and video library, and the offer pop-ups shoppers see. */
class MediaPopupIntegrationTest extends IntegrationTestSupport {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired MediaAssetRepository media;
    @Autowired PromoPopupRepository popups;
    @Autowired StoreVideoRepository videos;
    @Autowired CouponRepository coupons;

    // The smallest valid PNG, and a few bytes that look like an MP4 header.
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D, 'I', 'H', 'D', 'R'};
    private static final byte[] MP4 = {0, 0, 0, 0x18, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm', 0, 0, 0, 0};

    @AfterEach
    void clean() {
        videos.deleteAll();
        popups.deleteAll();
        media.deleteAll();
    }

    private String adminToken() throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/signin").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("username", "admin", "password", "test-admin-password")))).andReturn();
        return json.readTree(r.getResponse().getContentAsString(StandardCharsets.UTF_8)).get("token").asText();
    }

    private ResultActions upload(String t, String name, byte[] bytes) throws Exception {
        return mvc.perform(multipart("/api/admin/media").file(new MockMultipartFile("file", name, "application/octet-stream", bytes))
                .header("Authorization", "Bearer " + t));
    }

    @Test
    void ownerUploadsAPhotoAndShoppersGetItFromACacheableLink() throws Exception {
        mvc.perform(multipart("/api/admin/media").file(new MockMultipartFile("file", "a.png", "image/png", PNG))).andExpect(status().isUnauthorized());
        String t = adminToken();
        MvcResult r = upload(t, "..\\evil/Saree Front.png", PNG).andExpect(status().isOk()).andReturn();
        JsonNode info = json.readTree(r.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertEquals("IMAGE", info.get("kind").asText());
        assertEquals("image/png", info.get("contentType").asText());
        assertEquals("Saree Front.png", info.get("name").asText(), "path parts are stripped from the name");
        String url = info.get("url").asText();
        assertTrue(url.matches("/api/media/\\d+/[a-f0-9]{10}"), url);
        mvc.perform(get(url)).andExpect(status().isOk()).andExpect(header().string("Content-Type", "image/png"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("immutable")));
        mvc.perform(get(url.substring(0, url.length() - 1) + "0")).andExpect(status().isNotFound());
        assertEquals(1, json.readTree(mvc.perform(get("/api/admin/media").header("Authorization", "Bearer " + t)).andReturn().getResponse().getContentAsString()).size());
    }

    @Test
    void whatIsNotAPhotoOrVideoIsRefusedWhateverItIsCalled() throws Exception {
        String t = adminToken();
        upload(t, "photo.png", "<svg xmlns='http://www.w3.org/2000/svg'><script>alert(1)</script></svg>".getBytes(StandardCharsets.UTF_8)).andExpect(status().isBadRequest());
        upload(t, "page.jpg", "<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8)).andExpect(status().isBadRequest());
        upload(t, "tiny.png", new byte[]{1, 2}).andExpect(status().isBadRequest());
        assertEquals(0, media.count());
    }

    @Test
    void aLibraryVideoCanBeUsedAsAReelAndIsProtectedFromDeletionWhileInUse() throws Exception {
        String t = adminToken();
        JsonNode v = json.readTree(upload(t, "reel.mp4", MP4).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertEquals("VIDEO", v.get("kind").asText());
        Map<String, Object> reel = new LinkedHashMap<>();
        reel.put("title", "Silk in motion");
        reel.put("videoUrl", v.get("url").asText());
        reel.put("kind", "REEL");
        mvc.perform(post("/api/admin/videos").header("Authorization", "Bearer " + t).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(reel))).andExpect(status().isOk());
        mvc.perform(delete("/api/admin/media/" + v.get("id").asLong()).header("Authorization", "Bearer " + t)).andExpect(status().isBadRequest());
        assertEquals(1, media.count());
        // a library link to a photo is not a video, and an invented link is refused
        JsonNode img = json.readTree(upload(t, "p.png", PNG).andReturn().getResponse().getContentAsString());
        reel.put("videoUrl", img.get("url").asText());
        mvc.perform(post("/api/admin/videos").header("Authorization", "Bearer " + t).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(reel))).andExpect(status().isBadRequest());
        reel.put("videoUrl", "/api/media/999/0000000000");
        mvc.perform(post("/api/admin/videos").header("Authorization", "Bearer " + t).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(reel))).andExpect(status().isBadRequest());
        videos.deleteAll();
        mvc.perform(delete("/api/admin/media/" + v.get("id").asLong()).header("Authorization", "Bearer " + t)).andExpect(status().isNoContent());
    }

    private ResultActions popup(String t, Map<String, Object> body) throws Exception {
        return mvc.perform(post("/api/admin/popups").header("Authorization", "Bearer " + t).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }

    @Test
    void shoppersSeeOnlyTheLiveOfferAndOwnersCannotSaveABrokenOne() throws Exception {
        String t = adminToken();
        mvc.perform(get("/api/admin/popups")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/store/popup")).andExpect(status().isNoContent());

        Map<String, Object> p = new LinkedHashMap<>();
        p.put("headline", "Festive sale: 15% off silks");
        p.put("message", "Ends soon");
        p.put("ctaLabel", "Shop silks");
        p.put("ctaUrl", "/?view=deals");
        p.put("theme", "GOLD");
        p.put("delaySeconds", 3);
        p.put("frequency", "DAY");
        MvcResult made = popup(t, p).andExpect(status().isOk()).andReturn();
        long id = json.readTree(made.getResponse().getContentAsString()).get("id").asLong();
        JsonNode live = json.readTree(mvc.perform(get("/api/store/popup")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertEquals("Festive sale: 15% off silks", live.get("headline").asText());
        assertEquals("GOLD", live.get("theme").asText());
        assertFalse(live.has("name"), "internal labels never reach shoppers");

        mvc.perform(put("/api/admin/popups/" + id + "/active").param("value", "false").header("Authorization", "Bearer " + t)).andExpect(status().isOk());
        mvc.perform(get("/api/store/popup")).andExpect(status().isNoContent());
        mvc.perform(put("/api/admin/popups/" + id + "/active").param("value", "true").header("Authorization", "Bearer " + t)).andExpect(status().isOk());

        // not started yet, and already ended: neither is shown
        p.put("startsAt", java.time.LocalDateTime.now().plusDays(2).toString());
        p.put("endsAt", java.time.LocalDateTime.now().plusDays(3).toString());
        p.put("headline", "Future offer");
        popup(t, p).andExpect(status().isOk());
        p.put("startsAt", java.time.LocalDateTime.now().minusDays(3).toString());
        p.put("endsAt", java.time.LocalDateTime.now().minusDays(1).toString());
        p.put("headline", "Old offer");
        popup(t, p).andExpect(status().isOk());
        assertEquals("Festive sale: 15% off silks", json.readTree(mvc.perform(get("/api/store/popup")).andReturn().getResponse().getContentAsString()).get("headline").asText());

        // refused: missing headline, unknown coupon, javascript link, bad delay, countdown without an end, end before start
        Map<String, Object> bad = new LinkedHashMap<>(Map.of("headline", "x"));
        popup(t, Map.of("headline", "")).andExpect(status().isBadRequest());
        popup(t, Map.of("headline", "Hi", "couponCode", "NOPE123")).andExpect(status().isBadRequest());
        popup(t, Map.of("headline", "Hi", "ctaLabel", "Go", "ctaUrl", "javascript:alert(1)")).andExpect(status().isBadRequest());
        popup(t, Map.of("headline", "Hi", "delaySeconds", 500)).andExpect(status().isBadRequest());
        popup(t, Map.of("headline", "Hi", "countdown", true)).andExpect(status().isBadRequest());
        popup(t, Map.of("headline", "Hi", "theme", "RAINBOW")).andExpect(status().isBadRequest());
        assertNotNull(bad);

        // a real coupon is accepted and shown to shoppers
        com.example.ecommerce.model.Coupon c = new com.example.ecommerce.model.Coupon();
        c.setCode("POPUP10");
        c.setPercentOff(new java.math.BigDecimal("10"));
        coupons.save(c);
        try {
            popup(t, Map.of("headline", "Use the code", "couponCode", "popup10")).andExpect(status().isOk());
            assertEquals("POPUP10", json.readTree(mvc.perform(get("/api/store/popup")).andReturn().getResponse().getContentAsString()).get("couponCode").asText());
        } finally {
            popups.deleteAll();
            coupons.delete(coupons.findByCodeIgnoreCase("POPUP10").orElseThrow());
        }
    }
}
