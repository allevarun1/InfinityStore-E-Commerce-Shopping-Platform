package com.example.ecommerce.service.payment;

import com.example.ecommerce.model.Order;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * NON-PRODUCTION adapter that approves every payment instantly so the checkout flow can
 * be exercised without a payment provider. It refuses to start in a production environment.
 * Orders it handles are flagged testPayment and are excluded from sales reporting.
 */
@Component
@ConditionalOnProperty(name = "app.payment.provider", havingValue = "test")
public class TestPaymentGateway implements PaymentGateway {

    @Value("${app.env:production}")
    private String environment;

    @PostConstruct
    void refuseInProduction() {
        if ("production".equalsIgnoreCase(environment)) {
            throw new IllegalStateException("The test payment adapter must not be enabled in production");
        }
    }

    @Override
    public boolean enabled() {
        return true;
    }

    @Override
    public boolean testMode() {
        return true;
    }

    @Override
    public PaymentResult initiate(Order order) {
        return new PaymentResult(PaymentResult.Status.PAID, "TEST-" + UUID.randomUUID());
    }
}
