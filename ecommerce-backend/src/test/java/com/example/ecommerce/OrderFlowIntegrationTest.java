package com.example.ecommerce;

import com.example.ecommerce.dto.OrderRequest;
import com.example.ecommerce.model.*;
import com.example.ecommerce.service.BusinessException;
import com.example.ecommerce.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class OrderFlowIntegrationTest extends IntegrationTestSupport {
    @Autowired OrderService orderService;

    @Test
    void codOrderPersistsServerCalculatedBreakdownAndIsUnpaid() {
        User user = customer();
        Product p = product("500.00", 10);
        addToCart(user, p, 2);

        Order o = orderService.placeOrder(user, request("COD"), null);

        assertEquals("PENDING", o.getStatus());
        assertEquals("PENDING", o.getPaymentStatus(), "COD must not be recorded as paid");
        assertEquals("COD", o.getPaymentMethod());
        assertFalse(o.isTestPayment());
        // 2 x 500 = 1000, GST 18% = 180, shipping free (1000 > 999)
        assertEquals(new BigDecimal("1000.00"), o.getSubtotalAmount());
        assertEquals(new BigDecimal("180.00"), o.getTaxAmount());
        assertEquals(new BigDecimal("0.00"), o.getShippingAmount());
        assertEquals(new BigDecimal("1180.00"), o.getTotalPrice());
        assertEquals(OrderService.PRICING_VERSION, o.getPricingVersion());
        assertEquals("INR", o.getCurrency());
        assertTrue(o.getTrackingNumber().startsWith("INF-"));
        assertEquals(8, stockOf(p));
        assertTrue(carts.findByUser(user).isEmpty(), "cart is cleared after checkout");
    }

    @Test
    void orderItemsSnapshotPriceAndName() {
        User user = customer();
        Product p = product("300.00", 5);
        addToCart(user, p, 1);
        Order o = orderService.placeOrder(user, request("COD"), null);

        p.setPrice(new BigDecimal("999.00"));
        products.save(p);

        OrderItem item = orders.findById(o.getId()).orElseThrow().getOrderItems().get(0);
        assertEquals(new BigDecimal("300.00"), item.getPrice());
        assertEquals(p.getName(), item.getProductName());
        assertEquals(0, new BigDecimal("18").compareTo(item.getTaxPercent()));
    }

    @Test
    void onlinePaymentIsRejectedWhenNoProviderIsConfigured() {
        User user = customer();
        Product p = product("500.00", 10);
        addToCart(user, p, 1);

        for (String method : new String[] {"CARD", "UPI", "ONLINE"}) {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> orderService.placeOrder(user, request(method), null));
            assertTrue(ex.getMessage().contains("not available"));
        }
        assertEquals(10, stockOf(p), "rejected checkout must not touch stock");
        assertEquals(1, carts.findByUser(user).size(), "rejected checkout keeps the cart");
        assertFalse(orderService.paymentOptions().online());
    }

    @Test
    void insufficientStockFailsAtomically() {
        User user = customer();
        Product plenty = product("100.00", 10);
        Product scarce = product("100.00", 1);
        addToCart(user, plenty, 2);
        addToCart(user, scarce, 2);

        assertThrows(BusinessException.class, () -> orderService.placeOrder(user, request("COD"), null));

        assertEquals(10, stockOf(plenty));
        assertEquals(1, stockOf(scarce));
        assertEquals(0, orders.findByUserOrderByOrderDateDesc(user).size());
    }

    @Test
    void sameIdempotencyKeyReturnsTheSameOrderAndDeductsStockOnce() {
        User user = customer();
        Product p = product("200.00", 10);
        addToCart(user, p, 1);

        Order first = orderService.placeOrder(user, request("COD"), "key-123");
        Order second = orderService.placeOrder(user, request("COD"), "key-123");

        assertEquals(first.getId(), second.getId());
        assertEquals(1, orders.findByUserOrderByOrderDateDesc(user).size());
        assertEquals(9, stockOf(p));
    }

    @Test
    void secondCheckoutWithoutKeyOnEmptyCartCreatesNoDuplicate() {
        User user = customer();
        Product p = product("200.00", 10);
        addToCart(user, p, 1);

        orderService.placeOrder(user, request("COD"), null);
        assertThrows(BusinessException.class, () -> orderService.placeOrder(user, request("COD"), null));
        assertEquals(1, orders.findByUserOrderByOrderDateDesc(user).size());
    }

    @Test
    void cancellingRestoresStockOnce() {
        User user = customer();
        Product p = product("200.00", 10);
        addToCart(user, p, 3);
        Order o = orderService.placeOrder(user, request("COD"), null);
        assertEquals(7, stockOf(p));

        orderService.cancelByCustomer(user, o.getId());
        assertEquals(10, stockOf(p));
        assertEquals("CANCELLED", orders.findById(o.getId()).orElseThrow().getStatus());

        assertThrows(BusinessException.class, () -> orderService.updateOrderStatus(o.getId(), "CANCELLED"));
        assertEquals(10, stockOf(p), "a second cancel must not restore stock again");
    }

    @Test
    void customerCannotCancelAnotherCustomersOrder() {
        User owner = customer();
        User other = customer();
        Product p = product("200.00", 10);
        addToCart(owner, p, 1);
        Order o = orderService.placeOrder(owner, request("COD"), null);

        assertThrows(BusinessException.class, () -> orderService.cancelByCustomer(other, o.getId()));
        assertEquals("PENDING", orders.findById(o.getId()).orElseThrow().getStatus());
    }

    @Test
    void orderStatusFollowsTheStateMachine() {
        User user = customer();
        Product p = product("200.00", 10);
        addToCart(user, p, 1);
        Order o = orderService.placeOrder(user, request("COD"), null);

        assertThrows(BusinessException.class, () -> orderService.updateOrderStatus(o.getId(), "DELIVERED"));
        assertThrows(BusinessException.class, () -> orderService.updateOrderStatus(o.getId(), "NONSENSE"));

        assertEquals("SHIPPED", orderService.updateOrderStatus(o.getId(), "shipped").getStatus());
        assertThrows(BusinessException.class, () -> orderService.updateOrderStatus(o.getId(), "CANCELLED"),
                "a shipped order cannot be cancelled through the simple flow");
        assertEquals("DELIVERED", orderService.updateOrderStatus(o.getId(), "DELIVERED").getStatus());
        assertThrows(BusinessException.class, () -> orderService.updateOrderStatus(o.getId(), "PENDING"));
    }

    @Test
    void paymentStatusRulesAreEnforced() {
        User user = customer();
        Product p = product("200.00", 10);
        addToCart(user, p, 1);
        Order o = orderService.placeOrder(user, request("COD"), null);

        assertThrows(BusinessException.class, () -> orderService.updatePaymentStatus(o.getId(), "REFUNDED"),
                "cannot refund an unpaid order");
        assertEquals("PAID", orderService.updatePaymentStatus(o.getId(), "PAID").getPaymentStatus());
        verify(mail).sendPaymentSuccess(anyString(), any());
        assertThrows(BusinessException.class, () -> orderService.updatePaymentStatus(o.getId(), "PENDING"));
        assertEquals("REFUNDED", orderService.updatePaymentStatus(o.getId(), "REFUNDED").getPaymentStatus());
    }

    @Test
    void paidOrdersCannotBeCancelledByTheCustomer() {
        User user = customer();
        Product p = product("200.00", 10);
        addToCart(user, p, 1);
        Order o = orderService.placeOrder(user, request("COD"), null);
        orderService.updatePaymentStatus(o.getId(), "PAID");

        assertThrows(BusinessException.class, () -> orderService.cancelByCustomer(user, o.getId()));
    }

    @Test
    void privilegedChangesAreAudited() {
        User user = customer();
        Product p = product("200.00", 10);
        addToCart(user, p, 1);
        Order o = orderService.placeOrder(user, request("COD"), null);
        orderService.updateOrderStatus(o.getId(), "CONFIRMED");

        var entries = audit.findByEntityTypeAndEntityIdOrderByCreatedAtAsc("ORDER", o.getId());
        assertEquals(2, entries.size());
        assertEquals("ORDER_PLACED", entries.get(0).getAction());
        assertEquals("ORDER_STATUS_CHANGED", entries.get(1).getAction());
        assertEquals("PENDING -> CONFIRMED", entries.get(1).getDetail());
    }

    @Test
    void orderIsRefusedWhenTheTotalTheCustomerSawIsStale() {
        User user = customer();
        Product p = product("500.00", 10);
        addToCart(user, p, 1);
        OrderRequest r = request("COD");
        r.setExpectedTotal(new BigDecimal("1.00"));

        assertThrows(BusinessException.class, () -> orderService.placeOrder(user, r, null));
        assertEquals(10, stockOf(p), "no stock is taken for a refused order");
        assertEquals(1, carts.findByUser(user).size(), "the cart is untouched");

        r.setExpectedTotal(new BigDecimal("639.00")); // 500 + 90 GST + 49 shipping
        Order o = orderService.placeOrder(user, r, null);
        assertEquals(new BigDecimal("639.00"), o.getTotalPrice());
    }

    @Test
    void confirmationEmailIsNotSentWhenCheckoutFails() {
        User user = customer();
        Product p = product("200.00", 1);
        addToCart(user, p, 5);
        assertThrows(BusinessException.class, () -> orderService.placeOrder(user, request("COD"), null));
        verify(mail, never()).sendOrderPlaced(anyString(), any());
    }
}
