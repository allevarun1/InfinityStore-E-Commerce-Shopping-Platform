package com.example.ecommerce;

import com.example.ecommerce.model.Product;
import com.example.ecommerce.repository.StoreVideoRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Storefront videos: link validation, public visibility and owner-only management. */
class VideoIntegrationTest extends IntegrationTestSupport {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired StoreVideoRepository repo;

    @AfterEach
    void clean() { repo.deleteAll(); }

    private String adminToken() throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/signin").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("username", "admin", "password", "test-admin-password")))).andReturn();
        return json.readTree(r.getResponse().getContentAsString(StandardCharsets.UTF_8)).get("token").asText();
    }

    private Map<String, Object> req(String url) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("title", "From loom to you");
        m.put("kind", "STORY");
        m.put("videoUrl", url);
        return m;
    }

    private org.springframework.test.web.servlet.ResultActions add(String t, Map<String, Object> body) throws Exception {
        return mvc.perform(post("/api/admin/videos").header("Authorization", "Bearer " + t)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }

    @Test
    void onlySafeLinksAreAccepted() throws Exception {
        String t = adminToken();
        add(t, req("https://cdn.example.com/weaving.mp4?sig=1")).andExpect(status().isOk());
        add(t, req("https://youtu.be/dQw4w9WgXcQ")).andExpect(status().isOk());
        add(t, req("http://cdn.example.com/weaving.mp4")).andExpect(status().isBadRequest());
        add(t, req("javascript:alert(1)")).andExpect(status().isBadRequest());
        add(t, req("https://evil.example.com/watch?v=dQw4w9WgXcQ")).andExpect(status().isBadRequest());
        add(t, req("https://cdn.example.com/page.html")).andExpect(status().isBadRequest());
        add(t, req("https://user:pw@cdn.example.com/a.mp4")).andExpect(status().isBadRequest());
        Map<String, Object> badPoster = req("https://cdn.example.com/a.mp4");
        badPoster.put("posterUrl", "http://insecure.example.com/p.jpg");
        add(t, badPoster).andExpect(status().isBadRequest());
        Map<String, Object> noProduct = req("https://cdn.example.com/b.mp4");
        noProduct.put("productId", 999999);
        add(t, noProduct).andExpect(status().isBadRequest());
    }

    @Test
    void youtubeLinksBecomeAnIdAndFilesStayAsLinks() throws Exception {
        String t = adminToken();
        add(t, req("https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=5")).andExpect(status().isOk());
        add(t, req("https://www.youtube.com/shorts/abcdefghijk")).andExpect(status().isOk());
        add(t, req("https://cdn.example.com/loom.webm")).andExpect(status().isOk());
        MvcResult r = mvc.perform(get("/api/store/videos")).andExpect(status().isOk()).andReturn();
        JsonNode list = json.readTree(r.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertEquals(3, list.size());
        assertEquals("dQw4w9WgXcQ", list.get(0).get("youtubeId").asText());
        assertTrue(list.get(0).get("videoUrl").isNull());
        assertEquals("abcdefghijk", list.get(1).get("youtubeId").asText());
        assertEquals("https://cdn.example.com/loom.webm", list.get(2).get("videoUrl").asText());
    }

    @Test
    void paused_videosAreHiddenAndManagementNeedsTheOwner() throws Exception {
        mvc.perform(get("/api/admin/videos")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/admin/videos").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
        String t = adminToken();
        MvcResult r = add(t, req("https://cdn.example.com/a.mp4")).andExpect(status().isOk()).andReturn();
        long id = json.readTree(r.getResponse().getContentAsString(StandardCharsets.UTF_8)).get("id").asLong();
        mvc.perform(put("/api/admin/videos/" + id + "/active").param("value", "false").header("Authorization", "Bearer " + t)).andExpect(status().isOk());
        JsonNode pub = json.readTree(mvc.perform(get("/api/store/videos")).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertEquals(0, pub.size());
        JsonNode adm = json.readTree(mvc.perform(get("/api/admin/videos").header("Authorization", "Bearer " + t)).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertEquals(1, adm.size());
        mvc.perform(delete("/api/admin/videos/" + id).header("Authorization", "Bearer " + t)).andExpect(status().isOk());
        assertEquals(0, repo.count());
    }

    @Test
    void aVideoLinkedToASareeShowsOnlyForThatSareeAndOnlyWhileItIsPublished() throws Exception {
        String t = adminToken();
        Product p = product("1500.00", 3);
        Map<String, Object> body = req("https://cdn.example.com/drape.mp4");
        body.put("kind", "REEL");
        body.put("productId", p.getId());
        add(t, body).andExpect(status().isOk());
        add(t, req("https://cdn.example.com/other.mp4")).andExpect(status().isOk());
        JsonNode forP = json.readTree(mvc.perform(get("/api/store/videos").param("productId", String.valueOf(p.getId()))).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertEquals(1, forP.size());
        assertEquals(p.getName(), forP.get(0).get("productName").asText());
        p.setDeleted(true);
        products.save(p);
        JsonNode all = json.readTree(mvc.perform(get("/api/store/videos")).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertEquals(2, all.size());
        assertTrue(all.get(0).get("productId").isNull(), "a removed saree is no longer linked");
    }
}
