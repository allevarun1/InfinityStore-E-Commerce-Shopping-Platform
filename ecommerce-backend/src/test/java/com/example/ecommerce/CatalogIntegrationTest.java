package com.example.ecommerce;

import com.example.ecommerce.dto.CartItemRequest;
import com.example.ecommerce.dto.ProductRequest;
import com.example.ecommerce.model.*;
import com.example.ecommerce.service.BusinessException;
import com.example.ecommerce.service.CartService;
import com.example.ecommerce.service.OrderService;
import com.example.ecommerce.service.ProductService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.Base64;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

class CatalogIntegrationTest extends IntegrationTestSupport {
    private static final String PNG = "data:image/png;base64," + Base64.getEncoder().encodeToString(new byte[] {(byte) 0x89, 'P', 'N', 'G', 1, 2, 3});

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired ProductService productService;
    @Autowired CartService cartService;
    @Autowired OrderService orderService;
    @Value("${app.seed.admin.password}") String adminPassword;

    private ProductRequest productRequest(String name) {
        ProductRequest r = new ProductRequest();
        r.setName(name);
        r.setDescription("A handwoven saree with a detailed border and a matching blouse piece.");
        r.setPrice(new BigDecimal("1500.00"));
        r.setImageUrl("/images/x.jpg");
        r.setStockQuantity(5);
        r.setCategoryId(categories.findAll().stream().findFirst()
                .orElseGet(() -> categories.save(new Category("Test", "Test category"))).getId());
        return r;
    }

    private String token(String username, String password) throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/signin").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("username", username, "password", password)))).andReturn();
        assertEquals(200, r.getResponse().getStatus());
        return json.readTree(r.getResponse().getContentAsString()).get("token").asText();
    }

    private User customerWithPassword(String password) {
        User u = customer();
        u.setPassword(encoder.encode(password));
        return users.save(u);
    }

    // ---- lifecycle ----

    @Test
    void newProductsStartAsDraftAndAreInvisibleToShoppers() throws Exception {
        Product draft = productService.createProduct(productRequest("Draft saree one"));
        assertEquals(ProductStatus.DRAFT, draft.getStatus());

        mvc.perform(get("/api/products/" + draft.getId())).andExpect(r -> assertEquals(404, r.getResponse().getStatus()));
        String list = mvc.perform(get("/api/products")).andReturn().getResponse().getContentAsString();
        assertFalse(list.contains("Draft saree one"));
        String search = mvc.perform(get("/api/products/search").param("q", "Draft saree")).andReturn().getResponse().getContentAsString();
        assertFalse(search.contains("Draft saree one"));
    }

    @Test
    void draftsCannotBeAddedToCartOrWishlistOrBought() {
        Product draft = productService.createProduct(productRequest("Hidden saree two"));
        User u = customer();
        CartItemRequest req = new CartItemRequest();
        req.setProductId(draft.getId());
        req.setQuantity(1);
        assertThrows(BusinessException.class, () -> cartService.addItemToCart(u, req));

        // A product unpublished after it was carted must also fail at checkout.
        Product live = product("500.00", 5);
        addToCart(u, live, 1);
        productService.transition(live.getId(), ProductStatus.DRAFT);
        BusinessException ex = assertThrows(BusinessException.class, () -> orderService.placeOrder(u, request("COD"), null));
        assertTrue(ex.getMessage().contains("no longer available"));
        assertEquals(5, stockOf(live));
    }

    @Test
    void manualListingCanPublishDirectlyButOnlyWhenComplete() {
        Product p = productService.createProduct(productRequest("Manual saree three"));
        p = productService.transition(p.getId(), ProductStatus.PUBLISHED);
        assertEquals(ProductStatus.PUBLISHED, p.getStatus());

        ProductRequest incomplete = productRequest("Manual saree four");
        incomplete.setImageUrl(null);
        Product noImage = productService.createProduct(incomplete);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> productService.transition(noImage.getId(), ProductStatus.PUBLISHED));
        assertTrue(ex.getMessage().contains("image"));
        assertEquals(ProductStatus.DRAFT, products.findById(noImage.getId()).orElseThrow().getStatus());
    }

    @Test
    void aiAssistedListingMustPassEveryStepInOrder() {
        ProductRequest r = productRequest("AI saree five");
        r.setSource("AI_ASSISTED");
        Product p = productService.createProduct(r);
        Long id = p.getId();

        assertThrows(BusinessException.class, () -> productService.transition(id, ProductStatus.PUBLISHED));
        assertThrows(BusinessException.class, () -> productService.transition(id, ProductStatus.APPROVED));
        productService.transition(id, ProductStatus.READY_FOR_REVIEW);
        assertThrows(BusinessException.class, () -> productService.transition(id, ProductStatus.PUBLISHED));
        productService.transition(id, ProductStatus.APPROVED);
        assertEquals(ProductStatus.PUBLISHED, productService.transition(id, ProductStatus.PUBLISHED).getStatus());
    }

    @Test
    void lifecycleChangesAreAudited() {
        Product p = productService.createProduct(productRequest("Audited saree six"));
        productService.transition(p.getId(), ProductStatus.PUBLISHED);
        assertTrue(audit.findAll().stream().anyMatch(a -> "PRODUCT_STATUS".equals(a.getAction()) && p.getId().equals(a.getEntityId())));
    }

    @Test
    void publishedProductCannotBeEditedIntoAnIncompleteState() {
        Product p = productService.createProduct(productRequest("Published saree seven"));
        productService.transition(p.getId(), ProductStatus.PUBLISHED);
        ProductRequest edit = productRequest("Published saree seven");
        edit.setImageUrl(null);
        assertThrows(BusinessException.class, () -> productService.updateProduct(p.getId(), edit));
    }

    // ---- data protection ----

    @Test
    void costAndSupplierNeverReachShoppersButAdminsSeeMargin() throws Exception {
        ProductRequest r = productRequest("Margin saree eight");
        r.setCostPrice(new BigDecimal("900.00"));
        r.setSupplierName("Secret Weavers Cooperative");
        Product p = productService.createProduct(r);
        productService.transition(p.getId(), ProductStatus.PUBLISHED);

        for (String url : new String[] {"/api/products", "/api/products/" + p.getId(), "/api/products/search?q=Margin", "/api/products/deals"}) {
            String body = mvc.perform(get(url)).andReturn().getResponse().getContentAsString();
            assertFalse(body.contains("Secret Weavers"), url + " leaked the supplier");
            assertFalse(body.contains("costPrice"), url + " leaked cost");
            assertFalse(body.contains("\"rating\""), url + " exposed the placeholder rating");
        }

        // cart and wishlist embed the product entity; they must not leak either
        User u = customerWithPassword("shopper-pass-1");
        addToCart(u, p, 1);
        String cart = mvc.perform(get("/api/cart").header("Authorization", "Bearer " + token(u.getUsername(), "shopper-pass-1")))
                .andReturn().getResponse().getContentAsString();
        assertTrue(cart.contains("Margin saree eight"));
        assertFalse(cart.contains("Secret Weavers"));
        assertFalse(cart.contains("costPrice"));

        String adminToken = token("admin", adminPassword);
        String admin = mvc.perform(get("/api/admin/products").header("Authorization", "Bearer " + adminToken))
                .andReturn().getResponse().getContentAsString();
        JsonNode row = null;
        for (JsonNode n : json.readTree(admin)) if (n.get("id").asLong() == p.getId()) row = n;
        assertNotNull(row);
        assertEquals(900.0, row.get("costPrice").asDouble());
        assertEquals("Secret Weavers Cooperative", row.get("supplierName").asText());
        assertEquals(40.0, row.get("marginPercent").asDouble(), 0.01); // (1500-900)/1500
    }

    @Test
    void adminCatalogueAndLifecycleEndpointsRejectShoppersAndAnonymous() throws Exception {
        Product p = productService.createProduct(productRequest("Guarded saree nine"));
        User u = customerWithPassword("shopper-pass-2");
        String shopper = token(u.getUsername(), "shopper-pass-2");

        assertEquals(401, mvc.perform(get("/api/admin/products")).andReturn().getResponse().getStatus());
        assertEquals(403, mvc.perform(get("/api/admin/products").header("Authorization", "Bearer " + shopper)).andReturn().getResponse().getStatus());
        assertEquals(401, mvc.perform(put("/api/products/" + p.getId() + "/status").param("to", "PUBLISHED")).andReturn().getResponse().getStatus());
        assertEquals(403, mvc.perform(put("/api/products/" + p.getId() + "/status").param("to", "PUBLISHED")
                .header("Authorization", "Bearer " + shopper)).andReturn().getResponse().getStatus());
        assertEquals(ProductStatus.DRAFT, products.findById(p.getId()).orElseThrow().getStatus());
    }

    @Test
    void uploadedImagesAreServedFromACacheableEndpointNotInlinedInListings() throws Exception {
        ProductRequest r = productRequest("Image saree ten");
        r.setImageUrl(PNG);
        Product p = productService.createProduct(r);
        productService.transition(p.getId(), ProductStatus.PUBLISHED);

        String list = mvc.perform(get("/api/products")).andReturn().getResponse().getContentAsString();
        assertFalse(list.contains("data:image"), "listings must not carry base64 images");
        assertTrue(list.contains("/api/products/" + p.getId() + "/image?v="));

        MvcResult img = mvc.perform(get("/api/products/" + p.getId() + "/image")).andReturn();
        assertEquals(200, img.getResponse().getStatus());
        assertEquals("image/png", img.getResponse().getContentType());
        assertTrue(img.getResponse().getHeader("Cache-Control").contains("immutable"));
        assertEquals("nosniff", img.getResponse().getHeader("X-Content-Type-Options"));
        assertEquals(7, img.getResponse().getContentAsByteArray().length);
    }

    @Test
    void echoingTheServedImageLinkBackOnEditKeepsTheStoredPicture() throws Exception {
        ProductRequest r = productRequest("Echo saree eleven");
        r.setImageUrl(PNG);
        Product p = productService.createProduct(r);
        productService.transition(p.getId(), ProductStatus.PUBLISHED);

        ProductRequest edit = productRequest("Echo saree eleven");
        edit.setPrice(new BigDecimal("1600.00"));
        edit.setImageUrl(p.publicImageUrl()); // what the admin form sends back untouched
        productService.updateProduct(p.getId(), edit);

        Product reloaded = products.findById(p.getId()).orElseThrow();
        assertEquals(PNG, reloaded.getImageUrl(), "the uploaded image must not be replaced by its own URL");
        assertEquals(0, new BigDecimal("1600.00").compareTo(reloaded.getPrice()));
    }

    @Test
    void imageValidationBlocksSvgScriptAndOddSchemes() {
        for (String bad : new String[] {
                "data:image/svg+xml;base64,PHN2Zz48L3N2Zz4=", "javascript:alert(1)", "data:text/html;base64,PGgxPg==", "//evil.example/x.png", "ftp://x/y.png"}) {
            ProductRequest r = productRequest("Bad image " + bad.hashCode());
            r.setImageUrl(bad);
            assertThrows(BusinessException.class, () -> productService.createProduct(r), bad);
        }
    }

    // ---- reviews ----

    @Test
    void onlyDeliveredBuyersCanReviewAndOnlyOnce() throws Exception {
        Product p = product("500.00", 5);
        User buyer = customerWithPassword("buyer-pass-1");
        User stranger = customerWithPassword("stranger-pass-1");
        addToCart(buyer, p, 1);
        Order o = orderService.placeOrder(buyer, request("COD"), null);
        String buyerToken = token(buyer.getUsername(), "buyer-pass-1");
        String strangerToken = token(stranger.getUsername(), "stranger-pass-1");
        String review = json.writeValueAsString(Map.of("rating", 5, "title", "Lovely", "body", "Beautiful drape."));

        // not delivered yet
        assertEquals(400, submitReview(buyerToken, p, review).getResponse().getStatus());
        orderService.updateOrderStatus(o.getId(), "CONFIRMED");
        orderService.updateOrderStatus(o.getId(), "SHIPPED");
        orderService.updateOrderStatus(o.getId(), "DELIVERED");

        assertEquals(400, submitReview(strangerToken, p, review).getResponse().getStatus(), "never bought it");
        assertEquals(400, submitReview(buyerToken, p, json.writeValueAsString(Map.of("rating", 9))).getResponse().getStatus());
        assertEquals(200, submitReview(buyerToken, p, review).getResponse().getStatus());
        assertEquals(400, submitReview(buyerToken, p, review).getResponse().getStatus(), "second review is rejected");

        JsonNode detail = json.readTree(mvc.perform(get("/api/products/" + p.getId())).andReturn().getResponse().getContentAsString());
        assertEquals(1, detail.get("reviewCount").asInt());
        assertEquals(5.0, detail.get("averageRating").asDouble());

        String reviews = mvc.perform(get("/api/products/" + p.getId() + "/reviews")).andReturn().getResponse().getContentAsString();
        assertTrue(reviews.contains("Beautiful drape."));
        assertFalse(reviews.contains(buyer.getEmail()), "reviews are public and must not expose emails");
        assertFalse(reviews.contains(buyer.getUsername()));
    }

    @Test
    void productsWithoutReviewsShowNoRatingAndHiddenReviewsDropOut() throws Exception {
        Product p = product("500.00", 5);
        JsonNode none = json.readTree(mvc.perform(get("/api/products/" + p.getId())).andReturn().getResponse().getContentAsString());
        assertEquals(0, none.get("reviewCount").asInt());
        assertFalse(none.has("averageRating"), "no invented rating");

        User buyer = customerWithPassword("buyer-pass-2");
        addToCart(buyer, p, 1);
        Order o = orderService.placeOrder(buyer, request("COD"), null);
        orderService.updateOrderStatus(o.getId(), "CONFIRMED");
        orderService.updateOrderStatus(o.getId(), "SHIPPED");
        orderService.updateOrderStatus(o.getId(), "DELIVERED");
        String t = token(buyer.getUsername(), "buyer-pass-2");
        MvcResult created = submitReview(t, p, json.writeValueAsString(Map.of("rating", 2, "body", "Colour differs.")));
        long reviewId = json.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        String adminToken = token("admin", adminPassword);
        assertEquals(403, mvc.perform(put("/api/reviews/" + reviewId + "/hidden").param("hidden", "true")
                .header("Authorization", "Bearer " + t)).andReturn().getResponse().getStatus());
        assertEquals(204, mvc.perform(put("/api/reviews/" + reviewId + "/hidden").param("hidden", "true")
                .header("Authorization", "Bearer " + adminToken)).andReturn().getResponse().getStatus());

        JsonNode after = json.readTree(mvc.perform(get("/api/products/" + p.getId())).andReturn().getResponse().getContentAsString());
        assertEquals(0, after.get("reviewCount").asInt());
        assertEquals("[]", mvc.perform(get("/api/products/" + p.getId() + "/reviews")).andReturn().getResponse().getContentAsString());
    }

    private MvcResult submitReview(String token, Product p, String body) throws Exception {
        return mvc.perform(post("/api/reviews/product/" + p.getId())
                .contentType(MediaType.APPLICATION_JSON).content(body).header("Authorization", "Bearer " + token)).andReturn();
    }
}
