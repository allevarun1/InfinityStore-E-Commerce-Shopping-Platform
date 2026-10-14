package com.example.ecommerce.service.payment;

import com.example.ecommerce.model.Order;
import com.example.ecommerce.service.BusinessException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Default: no online payment provider is configured, so only cash on delivery is offered. */
@Component
@ConditionalOnProperty(name = "app.payment.provider", havingValue = "none", matchIfMissing = true)
public class DisabledPaymentGateway implements PaymentGateway {
    @Override
    public boolean enabled() {
        return false;
    }

    @Override
    public boolean testMode() {
        return false;
    }

    @Override
    public PaymentResult initiate(Order order) {
        throw new BusinessException("Online payment is not available yet. Please choose Cash on delivery.");
    }
}
