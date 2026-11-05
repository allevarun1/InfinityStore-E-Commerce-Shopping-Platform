package com.example.ecommerce;

import com.example.ecommerce.model.MediaAsset;
import com.example.ecommerce.repository.MediaAssetRepository;
import com.example.ecommerce.service.MediaService;
import com.example.ecommerce.service.storage.R2Storage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** With Cloudflare R2 configured, uploads leave the database and are served straight from R2. R2 itself is mocked. */
class R2MediaIntegrationTest extends IntegrationTestSupport {
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D, 'I', 'H', 'D', 'R'};

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired MediaAssetRepository media;
    @Autowired MediaService mediaService;
    @MockBean R2Storage r2;

    @BeforeEach
    void r2On() {
        when(r2.enabled()).thenReturn(true);
        when(r2.publicUrl(anyString())).thenAnswer(i -> "https://pub-test.r2.dev/" + i.getArgument(0));
    }

    @AfterEach
    void clean() {
        media.deleteAll();
    }

    private String adminToken() throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/signin").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("username", "admin", "password", "test-admin-password")))).andReturn();
        return json.readTree(r.getResponse().getContentAsString(StandardCharsets.UTF_8)).get("token").asText();
    }

    @Test
    void uploadGoesToR2AndTheLinkRedirectsThere() throws Exception {
        String t = adminToken();
        MvcResult r = mvc.perform(multipart("/api/admin/media").file(new MockMultipartFile("file", "Red Silk.png", "image/png", PNG))
                .header("Authorization", "Bearer " + t)).andExpect(status().isOk()).andReturn();
        JsonNode info = json.readTree(r.getResponse().getContentAsString(StandardCharsets.UTF_8));
        String url = info.get("url").asText();

        MediaAsset stored = media.findById(info.get("id").asLong()).orElseThrow();
        assertNull(stored.getData(), "the file is not kept in the database");
        assertEquals("media/" + stored.getChecksum(), stored.getStorageKey());
        verify(r2).put(eq(stored.getStorageKey()), eq(PNG), eq("image/png"));

        mvc.perform(get(url)).andExpect(status().isFound())
                .andExpect(header().string("Location", "https://pub-test.r2.dev/media/" + stored.getId() + "/" + stored.getChecksum()))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("immutable")));

        mvc.perform(delete("/api/admin/media/" + stored.getId()).header("Authorization", "Bearer " + t)).andExpect(status().isNoContent());
        verify(r2).delete(stored.getStorageKey());
    }

    @Test
    void identicalUploadsShareOneR2CopyUntilTheLastIsDeleted() throws Exception {
        String t = adminToken();
        long first = upload(t, "a.png"), second = upload(t, "b.png");
        String key = media.findById(first).orElseThrow().getStorageKey();
        assertEquals(key, media.findById(second).orElseThrow().getStorageKey());

        mvc.perform(delete("/api/admin/media/" + first).header("Authorization", "Bearer " + t)).andExpect(status().isNoContent());
        verify(r2, never()).delete(anyString());
        mvc.perform(delete("/api/admin/media/" + second).header("Authorization", "Bearer " + t)).andExpect(status().isNoContent());
        verify(r2).delete(key);
    }

    private long upload(String token, String name) throws Exception {
        MvcResult r = mvc.perform(multipart("/api/admin/media").file(new MockMultipartFile("file", name, "image/png", PNG))
                .header("Authorization", "Bearer " + token)).andExpect(status().isOk()).andReturn();
        return json.readTree(r.getResponse().getContentAsString(StandardCharsets.UTF_8)).get("id").asLong();
    }

    @Test
    void failedR2UploadSavesNothing() throws Exception {
        doThrow(new IllegalStateException("down")).when(r2).put(anyString(), any(), anyString());
        mvc.perform(multipart("/api/admin/media").file(new MockMultipartFile("file", "a.png", "image/png", PNG))
                .header("Authorization", "Bearer " + adminToken())).andExpect(status().isBadRequest());
        assertEquals(0, media.count());
    }

    @Test
    void filesUploadedBeforeR2AreMovedOutOfTheDatabase() {
        MediaAsset old = new MediaAsset();
        old.setName("old.png");
        old.setContentType("image/png");
        old.setKind(MediaAsset.Kind.IMAGE);
        old.setSizeBytes(PNG.length);
        old.setChecksum("abcdef1234");
        old.setData(PNG);
        Long id = media.save(old).getId();

        mediaService.moveDatabaseFilesToR2();

        MediaAsset moved = media.findById(id).orElseThrow();
        assertNull(moved.getData());
        assertNotNull(moved.getStorageKey());
        verify(r2).put(eq(moved.getStorageKey()), eq(PNG), eq("image/png"));
    }
}
