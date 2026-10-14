package com.example.ecommerce;

import com.example.ecommerce.dto.OrderRequest;
import com.example.ecommerce.model.*;
import com.example.ecommerce.repository.*;
import com.example.ecommerce.service.MailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/** Shared fixtures. Mail is mocked so no test can send a real message. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
abstract class IntegrationTestSupport {
    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired protected UserRepository users;
    @Autowired protected RoleRepository roles;
    @Autowired protected ProductRepository products;
    @Autowired protected CategoryRepository categories;
    @Autowired protected CartItemRepository carts;
    @Autowired protected OrderRepository orders;
    @Autowired protected AuditLogRepository audit;
    @Autowired protected PasswordEncoder encoder;

    @MockBean protected MailService mail;

    protected User customer() {
        int n = SEQ.incrementAndGet();
        User u = new User("cust" + n, "cust" + n + "@test.local", encoder.encode("password-" + n), "Test", "Customer");
        u.setRoles(Set.of(roles.findByName(ERole.ROLE_CUSTOMER).orElseThrow()));
        return users.save(u);
    }

    protected Product product(String price, int stock) {
        Category c = categories.findAll().stream().findFirst()
                .orElseGet(() -> categories.save(new Category("Test", "Test category")));
        Product p = new Product("Item " + SEQ.incrementAndGet(), "Description", new BigDecimal(price), "/x.jpg", stock, c);
        p.setDeal(false);
        return products.save(p);
    }

    protected void addToCart(User user, Product product, int qty) {
        carts.save(new CartItem(user, product, qty));
    }

    protected OrderRequest request(String method) {
        OrderRequest r = new OrderRequest();
        r.setShippingAddress("12 Test Street, Bengaluru");
        r.setPhoneNumber("9876543210");
        r.setPaymentMethod(method);
        return r;
    }

    protected int stockOf(Product p) {
        return products.findById(p.getId()).orElseThrow().getStockQuantity();
    }
}
