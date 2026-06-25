package com.icentric.Icentric.common.ratelimit;

import com.icentric.Icentric.common.security.SecurityUtils;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;

/**
 * Rate limits POST requests to /api/v1/simulations/* to 10 requests per minute per user.
 */
@Component
public class SimulationRateLimitFilter implements Filter {

    private static final Duration WINDOW = Duration.ofSeconds(5);
    private static final String PREFIX = "sim_rate:";

    private final DatabaseRateLimiterService rateLimiter;

    public SimulationRateLimitFilter(DatabaseRateLimiterService rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpReq = (HttpServletRequest) request;
        String path = httpReq.getRequestURI();
        String method = httpReq.getMethod();

        // Only rate-limit POST on simulation endpoints
        if ("POST".equals(method) && path.startsWith("/api/v1/simulations/")) {
            UUID userId = SecurityUtils.currentUserIdOrNull();
            if (userId != null) {
                String key = PREFIX + userId;
                if (!rateLimiter.acquireLock(key, WINDOW)) {
                    HttpServletResponse httpResp = (HttpServletResponse) response;
                    httpResp.setStatus(429);
                    httpResp.setContentType("application/json");
                    httpResp.getWriter().write("{\"detail\":\"Too many requests. Please wait a few seconds.\"}");
                    return;
                }
            }
        }

        chain.doFilter(request, response);
    }
}
