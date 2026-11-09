package com.example.ecommerce.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Origin lock. Cloudflare adds a secret header to every request it forwards (a Transform Rule on the api
 * hostname); when ORIGIN_SECRET is set, anything arriving without it, such as a scanner hitting the raw
 * onrender.com address, is refused before it reaches the app. With no secret configured the filter does
 * nothing, so deploying it is safe until the lock is switched on.
 *
 * Two paths stay open on purpose: Razorpay's server-to-server webhook (it proves itself with a signature
 * and Bot Fight Mode could challenge it if it went through Cloudflare) and /api/store/info (Render's health
 * check and the uptime monitor, which only return public store details).
 */
public class OriginSecretFilter extends OncePerRequestFilter {
    static final String HEADER = "X-Origin-Secret";

    private final byte[] secret;

    public OriginSecretFilter(String secret) {
        this.secret = secret == null || secret.isBlank() ? null : secret.trim().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (secret == null || isExempt(request) || matches(request.getHeader(HEADER))) {
            chain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        response.getWriter().write("{\"message\":\"Forbidden\"}");
    }

    private boolean isExempt(HttpServletRequest request) {
        String method = request.getMethod();
        String path = request.getServletPath();
        if ("OPTIONS".equalsIgnoreCase(method)) {
            return true; // CORS preflight carries no data and the browser cannot attach custom headers to it
        }
        if ("POST".equalsIgnoreCase(method) && "/api/payments/razorpay/webhook".equals(path)) {
            return true;
        }
        return ("GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method)) && "/api/store/info".equals(path);
    }

    private boolean matches(String presented) {
        return presented != null
                && MessageDigest.isEqual(secret, presented.trim().getBytes(StandardCharsets.UTF_8));
    }
}
