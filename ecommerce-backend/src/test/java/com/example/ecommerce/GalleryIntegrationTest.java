package com.example.ecommerce;

import com.example.ecommerce.dto.ProductRequest;
import com.example.ecommerce.model.Category;
import com.example.ecommerce.model.Product;
import com.example.ecommerce.model.ProductStatus;
import com.example.ecommerce.service.BusinessException;
import com.example.ecommerce.service.ProductService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

class GalleryIntegrationTest extends IntegrationTestSupport {
    private static final byte[] PNG_BYTES = {(byte) 0x89, 'P', 'N', 'G', 7, 7, 7};
    private static final String PNG = "data:image/png;base64," + Base64.getEncoder().encodeToString(PNG_BYTES);

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired ProductService productService;

    private ProductRequest productRequest(List<String> gallery) {
        ProductRequest r = new ProductRequest();
        r.setName("Gallery saree");
        r.setDescription("A handwoven saree photographed from several angles for shoppers.");
        r.setPrice(new BigDecimal("2500.00"));
        r.setImageUrl("/images/main.jpg");
        r.setStockQuantity(4);
        r.setCategoryId(categories.findAll().stream().findFirst()
                .orElseGet(() -> categories.save(new Category("Test", "Test category"))).getId());
        r.setGallery(gallery);
        return r;
    }

    private JsonNode publicView(Long id) throws Exception {
        MvcResult r = mvc.perform(get("/api/products/" + id)).andReturn();
        assertEquals(200, r.getResponse().getStatus());
        return json.readTree(r.getResponse().getContentAsString());
    }

    @Test
    void galleryIsStoredInOrderAndUploadedPhotosAreServedFromCacheableLinks() throws Exception {
        Product p = productService.createProduct(productRequest(List.of("https://cdn.example.com/drape.jpg", PNG)));
        productService.transition(p.getId(), ProductStatus.PUBLISHED);

        JsonNode gallery = publicView(p.getId()).get("gallery");
        assertEquals(2, gallery.size());
        assertEquals("https://cdn.example.com/drape.jpg", gallery.get(0).asText());
        String served = gallery.get(1).asText();
        assertTrue(served.startsWith("/api/products/" + p.getId() + "/gallery/1?v="), served);
        assertFalse(publicView(p.getId()).toString().contains("base64"), "raw uploads never reach the listing JSON");

        MvcResult img = mvc.perform(get(served)).andReturn();
        assertEquals(200, img.getResponse().getStatus());
        assertEquals("image/png", img.getResponse().getContentType());
        assertArrayEquals(PNG_BYTES, img.getResponse().getContentAsByteArray());
        assertEquals(404, mvc.perform(get("/api/products/" + p.getId() + "/gallery/0")).andReturn().getResponse().getStatus(),
                "external links are not proxied");
    }

    @Test
    void savingTheEchoedPublicLinksKeepsUploadsAndAllowsReorderAndRemoval() throws Exception {
        Product p = productService.createProduct(productRequest(List.of(PNG, "/images/side.jpg", "/images/back.jpg")));
        productService.transition(p.getId(), ProductStatus.PUBLISHED);
        JsonNode g = publicView(p.getId()).get("gallery");

        // admin drops the side photo and moves the upload last
        List<String> edited = new ArrayList<>(List.of(g.get(2).asText(), g.get(0).asText()));
        productService.updateProduct(p.getId(), productRequest(edited));

        List<String> stored = products.findById(p.getId()).orElseThrow().getGalleryRaw();
        assertEquals(List.of("/images/back.jpg", PNG), stored);
    }

    @Test
    void unsafeOrTooManyPhotosAreRefused() {
        assertThrows(BusinessException.class,
                () -> productService.createProduct(productRequest(List.of("data:image/svg+xml;base64,PHN2Zz48L3N2Zz4="))));
        assertThrows(BusinessException.class,
                () -> productService.createProduct(productRequest(List.of("javascript:alert(1)"))));
        assertThrows(BusinessException.class,
                () -> productService.createProduct(productRequest(Collections.nCopies(7, "/images/x.jpg"))));
    }

    @Test
    void productsWithoutAGalleryReturnAnEmptyList() throws Exception {
        Product p = productService.createProduct(productRequest((List<String>) null));
        productService.transition(p.getId(), ProductStatus.PUBLISHED);
        assertEquals(0, publicView(p.getId()).get("gallery").size());
    }
}
