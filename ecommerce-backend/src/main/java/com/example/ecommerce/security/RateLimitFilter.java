package com.example.ecommerce.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/** Per-client-IP throttling of the unauthenticated endpoints (accounts, sign-ups, the shopping assistant). */
public class RateLimitFilter extends OncePerRequestFilter {
    private record Rule(String path, int limit, long windowMillis) {
    }

    private final RateLimiter limiter;
    private final List<Rule> rules;

    public RateLimitFilter(RateLimiter limiter, int multiplier) {
        this.limiter = limiter;
        long minute = 60_000L;
        this.rules = List.of(
                new Rule("/api/auth/signin", 30 * multiplier, 5 * minute),
                new Rule("/api/auth/signup", 10 * multiplier, 60 * minute),
                new Rule("/api/auth/forgot-password", 5 * multiplier, 15 * minute),
                new Rule("/api/auth/reset-password", 10 * multiplier, 15 * minute),
                new Rule("/api/store/subscribe", 5 * multiplier, 60 * minute),
                new Rule("/api/store/leads", 10 * multiplier, 60 * minute),
                new Rule("/api/store/track", 15 * multiplier, 15 * minute),
                new Rule("/api/assistant/chat", 40 * multiplier, 5 * minute));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if ("POST".equalsIgnoreCase(request.getMethod())) {
            String path = request.getServletPath();
            for (Rule rule : rules) {
                if (rule.path().equals(path)) {
                    long retryAfter = limiter.tryAcquire(path + "|" + request.getRemoteAddr(), rule.limit(),
                            rule.windowMillis());
                    if (retryAfter > 0) {
                        response.setStatus(429);
                        response.setHeader("Retry-After", String.valueOf(retryAfter));
                        response.setContentType("application/json");
                        response.getWriter().write("{\"message\":\"Too many requests. Please try again later.\"}");
                        return;
                    }
                }
            }
        }
        chain.doFilter(request, response);
    }
}
