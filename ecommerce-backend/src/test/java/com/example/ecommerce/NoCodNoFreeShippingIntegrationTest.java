package com.example.ecommerce;

import com.example.ecommerce.dto.PriceQuote;
import com.example.ecommerce.model.Product;
import com.example.ecommerce.model.User;
import com.example.ecommerce.service.BusinessException;
import com.example.ecommerce.service.OrderService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/** A shop with no cash on delivery and no free shipping: a flat fee on every order, online payment only. */
@TestPropertySource(properties = {
        "app.payment.cod-enabled=false",
        "app.pricing.free-shipping-threshold=0",
        "app.pricing.shipping-fee=99"
})
class NoCodNoFreeShippingIntegrationTest extends IntegrationTestSupport {
    @Autowired OrderService orderService;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Test
    void everyOrderPaysTheFlatDeliveryFee() {
        User user = customer();
        addToCart(user, product("5000.00", 5), 1);
        PriceQuote quote = orderService.quoteCart(user);
        assertEquals(0, new BigDecimal("99.00").compareTo(quote.shipping()), "no free shipping, even on a big order");
    }

    @Test
    void cashOnDeliveryOrdersAreRefusedAndNothingChanges() {
        User user = customer();
        Product p = product("1200.00", 5);
        addToCart(user, p, 1);
        BusinessException e = assertThrows(BusinessException.class, () -> orderService.placeOrder(user, request("COD"), null));
        assertTrue(e.getMessage().contains("Cash on delivery is not available"));
        assertEquals(5, stockOf(p), "stock untouched");
        assertEquals(1, carts.findByUser(user).size(), "cart kept");
    }

    @Test
    void storeInfoTellsTheSiteNotToMentionCodOrFreeShipping() throws Exception {
        JsonNode info = json.readTree(mvc.perform(get("/api/store/info")).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertFalse(info.get("cashOnDelivery").asBoolean());
        assertTrue(info.get("freeShippingThreshold").isNull());
        assertEquals(0, new BigDecimal("99").compareTo(info.get("shippingFee").decimalValue()));
    }
}
