package com.example.ecommerce;

import com.example.ecommerce.model.Product;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The in-memory catalog never shows a stale saree: any saved change clears it. */
class CatalogCacheIntegrationTest extends IntegrationTestSupport {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private JsonNode product(Long id) throws Exception {
        return json.readTree(mvc.perform(get("/api/products/" + id)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private boolean listed(Long id) throws Exception {
        JsonNode list = json.readTree(mvc.perform(get("/api/products")).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        for (JsonNode p : list) if (p.get("id").asLong() == id) return true;
        return false;
    }

    @Test
    void priceStockAndNewSareesShowImmediately() throws Exception {
        Product p = product("1000.00", 5);
        assertEquals(0, new BigDecimal("1000.00").compareTo(new BigDecimal(product(p.getId()).get("price").asText())));
        assertTrue(listed(p.getId()));

        Product saved = products.findById(p.getId()).orElseThrow();
        saved.setPrice(new BigDecimal("1499.00"));
        saved.setStockQuantity(2);
        products.save(saved);

        JsonNode after = product(p.getId());
        assertEquals(0, new BigDecimal("1499.00").compareTo(new BigDecimal(after.get("price").asText())), "new price at once");
        assertEquals(2, after.get("stockQuantity").asInt(), "stock at once");

        Product added = product("700.00", 3);
        assertTrue(listed(added.getId()), "a new saree appears in the list at once");
    }
}
