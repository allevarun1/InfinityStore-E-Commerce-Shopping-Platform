package com.example.ecommerce;

import com.example.ecommerce.security.OriginSecretFilter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class OriginSecretFilterTest {
    private static MockHttpServletResponse send(OriginSecretFilter filter, String method, String path, String header)
            throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setServletPath(path);
        if (header != null) {
            request.addHeader("X-Origin-Secret", header);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        // a request that passes through leaves the mock's default 200; a refused one gets 403 from the filter
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    @Test
    void offWhenNoSecretIsConfigured() throws Exception {
        assertEquals(200, send(new OriginSecretFilter(""), "GET", "/api/products", null).getStatus());
        assertEquals(200, send(new OriginSecretFilter(null), "POST", "/api/auth/signin", null).getStatus());
    }

    @Test
    void refusesRequestsWithoutTheRightSecretWhenConfigured() throws Exception {
        OriginSecretFilter filter = new OriginSecretFilter("s3cret-value");
        assertEquals(403, send(filter, "GET", "/api/products", null).getStatus());
        assertEquals(403, send(filter, "GET", "/api/products", "wrong").getStatus());
        assertEquals(403, send(filter, "POST", "/api/auth/signin", "").getStatus());
        assertEquals(200, send(filter, "GET", "/api/products", "s3cret-value").getStatus());
        assertEquals(200, send(filter, "POST", "/api/auth/signin", " s3cret-value ").getStatus());
    }

    @Test
    void keepsWebhookHealthCheckAndPreflightOpen() throws Exception {
        OriginSecretFilter filter = new OriginSecretFilter("s3cret-value");
        assertEquals(200, send(filter, "POST", "/api/payments/razorpay/webhook", null).getStatus());
        assertEquals(200, send(filter, "GET", "/api/store/info", null).getStatus());
        assertEquals(200, send(filter, "HEAD", "/api/store/info", null).getStatus());
        assertEquals(200, send(filter, "OPTIONS", "/api/orders/checkout", null).getStatus());
        // only those exact doors: other store paths, other methods and look-alike paths stay locked
        assertEquals(403, send(filter, "GET", "/api/store/offers", null).getStatus());
        assertEquals(403, send(filter, "GET", "/api/payments/razorpay/webhook", null).getStatus());
        assertEquals(403, send(filter, "POST", "/api/store/info", null).getStatus());
        assertEquals(403, send(filter, "GET", "/api/store/info/extra", null).getStatus());
    }

    @Test
    void rejectionBodyRevealsNothing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/admin/users");
        request.setServletPath("/api/admin/users");
        MockHttpServletResponse response = new MockHttpServletResponse();
        new OriginSecretFilter("s3cret-value").doFilter(request, response, new MockFilterChain());
        assertEquals(403, response.getStatus());
        assertEquals("{\"message\":\"Forbidden\"}", response.getContentAsString());
        assertNull(response.getHeader("X-Origin-Secret"));
        assertNotNull(response.getContentType());
    }
}
