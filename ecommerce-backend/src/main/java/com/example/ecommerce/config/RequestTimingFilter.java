package com.example.ecommerce.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Logs API calls that take longer than a second (with method, path and time) so slowness shows up in the Render logs,
 * and tells the browser how long the server spent (Server-Timing, visible in the Network tab).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestTimingFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(RequestTimingFilter.class);
    private static final long SLOW_MS = 1000;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long start = System.nanoTime();
        response.addHeader("Timing-Allow-Origin", "*");
        try {
            chain.doFilter(request, new jakarta.servlet.http.HttpServletResponseWrapper(response) {
                private boolean added;

                private void addTiming() {
                    if (!added && !isCommitted()) {
                        added = true;
                        response.addHeader("Server-Timing", "app;dur=" + (System.nanoTime() - start) / 1_000_000);
                    }
                }

                @Override
                public jakarta.servlet.ServletOutputStream getOutputStream() throws IOException {
                    addTiming();
                    return super.getOutputStream();
                }

                @Override
                public java.io.PrintWriter getWriter() throws IOException {
                    addTiming();
                    return super.getWriter();
                }
            });
        } finally {
            long ms = (System.nanoTime() - start) / 1_000_000;
            if (ms >= SLOW_MS) {
                log.info("Slow request: {} {} took {} ms (status {})", request.getMethod(), request.getRequestURI(), ms, response.getStatus());
            }
        }
    }
}
