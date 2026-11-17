package com.harshshah.matchingengine.controller;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.LongSupplier;

@Component
@ConditionalOnProperty(name = "demo.public-guard.enabled", havingValue = "true")
public class PublicDemoGuard extends OncePerRequestFilter {
    private final Budget reads = new Budget(6000, System::nanoTime);
    private final Budget writes = new Budget(120, System::nanoTime);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
        response.setHeader("Content-Security-Policy", "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; connect-src 'self'; object-src 'none'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'");
        if (request.getRequestURI().startsWith("/api/")) {
            boolean read = request.getMethod().equals("GET") || request.getMethod().equals("HEAD");
            if (!read && request.getContentLengthLong() > 16384) {
                reject(response, 413, "Request body is too large.");
                return;
            }
            if (!(read ? reads : writes).allow()) {
                response.setHeader("Retry-After", "60");
                reject(response, 429, "The shared demo is busy. Try again shortly.");
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private void reject(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setHeader("Cache-Control", "no-store");
        response.getWriter().write("{\"message\":\"" + message + "\"}");
    }

    static final class Budget {
        private final int limit;
        private final LongSupplier clock;
        private final Deque<Long> requests = new ArrayDeque<>();

        Budget(int limit, LongSupplier clock) {
            this.limit = limit;
            this.clock = clock;
        }

        synchronized boolean allow() {
            long now = clock.getAsLong();
            long cutoff = now - 60_000_000_000L;
            while (!requests.isEmpty() && requests.peekFirst() <= cutoff) requests.removeFirst();
            if (requests.size() >= limit) return false;
            requests.addLast(now);
            return true;
        }
    }
}
