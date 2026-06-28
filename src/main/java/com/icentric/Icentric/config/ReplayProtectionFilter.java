package com.icentric.Icentric.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Validates X-Request-Timestamp (±5 min) and rejects duplicate X-Request-Id.
 * Only enforced on non-GET, non-OPTIONS, non-public endpoints.
 */
@Component
@Order(2)
public class ReplayProtectionFilter implements Filter {

    private static final long MAX_SKEW_SECONDS = 300; // 5 minutes
    private static final int MAX_SEEN_IDS = 10_000;

    // LRU set of recently seen request IDs
    private final Set<String> seenIds = Collections.newSetFromMap(
            Collections.synchronizedMap(new LinkedHashMap<>(MAX_SEEN_IDS + 1, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                    return size() > MAX_SEEN_IDS;
                }
            })
    );

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest req = (HttpServletRequest) request;
        String method = req.getMethod();
        String path = req.getRequestURI();

        // Skip GET, OPTIONS, and public auth endpoints
        if ("GET".equals(method) || "OPTIONS".equals(method) || isPublicPath(path)) {
            chain.doFilter(request, response);
            return;
        }

        // Validate timestamp
        String timestamp = req.getHeader("X-Request-Timestamp");
        if (timestamp != null) {
            try {
                long requestEpoch = Long.parseLong(timestamp);
                long nowEpoch = Instant.now().getEpochSecond();
                if (Math.abs(nowEpoch - requestEpoch) > MAX_SKEW_SECONDS) {
                    reject((HttpServletResponse) response, 400, "Request timestamp expired");
                    return;
                }
            } catch (NumberFormatException e) {
                reject((HttpServletResponse) response, 400, "Invalid X-Request-Timestamp");
                return;
            }
        }

        // Reject duplicate request ID
        String requestId = req.getHeader("X-Request-Id");
        if (requestId != null && !requestId.isBlank()) {
            if (!seenIds.add(requestId)) {
                reject((HttpServletResponse) response, 409, "Duplicate request");
                return;
            }
        }

        chain.doFilter(request, response);
    }

    private boolean isPublicPath(String path) {
        return path.startsWith("/api/v1/auth/") || path.startsWith("/api/v1/platform/auth/")
                || path.startsWith("/api/v1/public/") || path.startsWith("/swagger")
                || path.startsWith("/v3/api-docs") || path.startsWith("/oauth2");
    }

    private void reject(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write("{\"detail\":\"" + message + "\"}");
    }
}
