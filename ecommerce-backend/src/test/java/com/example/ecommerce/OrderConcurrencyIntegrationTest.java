package com.example.ecommerce;

import com.example.ecommerce.model.Product;
import com.example.ecommerce.model.User;
import com.example.ecommerce.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OrderConcurrencyIntegrationTest extends IntegrationTestSupport {
    @Autowired OrderService orderService;

    @Test
    void lastUnitIsSoldExactlyOnceUnderConcurrentCheckouts() throws Exception {
        final int buyers = 6;
        Product p = product("250.00", 1);
        List<User> customers = new ArrayList<>();
        for (int i = 0; i < buyers; i++) {
            User u = customer();
            addToCart(u, p, 1);
            customers.add(u);
        }

        ExecutorService pool = Executors.newFixedThreadPool(buyers);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        for (User u : customers) {
            results.add(pool.submit(() -> {
                start.await();
                try {
                    orderService.placeOrder(u, request("COD"), null);
                    return true;
                } catch (RuntimeException e) {
                    return false;
                }
            }));
        }
        start.countDown();
        int succeeded = 0;
        for (Future<Boolean> f : results) {
            if (f.get(60, TimeUnit.SECONDS)) {
                succeeded++;
            }
        }
        pool.shutdown();

        assertEquals(1, succeeded, "exactly one buyer may get the last unit");
        assertEquals(0, stockOf(p), "stock never goes negative");
    }

    @Autowired com.example.ecommerce.service.CartService cartService;

    @Test
    void concurrentAddToCartOfTheSameProductMergesIntoOneLine() throws Exception {
        Product p = product("250.00", 10);
        User u = customer();
        com.example.ecommerce.dto.CartItemRequest req = new com.example.ecommerce.dto.CartItemRequest();
        req.setProductId(p.getId());
        req.setQuantity(1);

        ExecutorService pool = Executors.newFixedThreadPool(4);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> results = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            results.add(pool.submit(() -> { start.await(); return cartService.addItemToCart(u, req); }));
        }
        start.countDown();
        for (Future<?> f : results) {
            f.get(60, TimeUnit.SECONDS);
        }
        pool.shutdown();

        var lines = carts.findByUser(u);
        assertEquals(1, lines.size(), "one cart line per product");
        assertEquals(4, lines.get(0).getQuantity());
    }

    @Test
    void concurrentDoubleSubmitByOneCustomerCreatesOneOrder() throws Exception {
        Product p = product("250.00", 10);
        User u = customer();
        addToCart(u, p, 1);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            results.add(pool.submit(() -> {
                start.await();
                try {
                    orderService.placeOrder(u, request("COD"), "same-click");
                    return true;
                } catch (RuntimeException e) {
                    return false;
                }
            }));
        }
        start.countDown();
        for (Future<Boolean> f : results) {
            f.get(60, TimeUnit.SECONDS);
        }
        pool.shutdown();

        assertEquals(1, orders.findByUserOrderByOrderDateDesc(u).size());
        assertEquals(9, stockOf(p));
    }
}
