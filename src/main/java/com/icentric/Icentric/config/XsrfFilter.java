package com.icentric.Icentric.config;

import jakarta.servlet.*;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

/**
 * XSRF/CSRF protection:
 * - Sets XSRF-TOKEN cookie on every response (readable by Angular/frontend)
 * - Validates X-XSRF-TOKEN header matches cookie on mutating requests (POST/PUT/PATCH/DELETE)
 * - Skips public/auth endpoints
 */
@Component
@Order(3)
public class XsrfFilter implements Filter {

    private static final String COOKIE_NAME = "XSRF-TOKEN";
    private static final String HEADER_NAME = "X-XSRF-TOKEN";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;
        String method = req.getMethod();
        String path = req.getRequestURI();

        // Get or generate XSRF token
        String token = getTokenFromCookie(req);
        if (token == null) {
            token = UUID.randomUUID().toString();
        }

        // Always set/refresh the cookie
        Cookie cookie = new Cookie(COOKIE_NAME, token);
        cookie.setPath("/");
        cookie.setHttpOnly(false); // must be readable by JS
        cookie.setSecure(req.isSecure());
        cookie.setMaxAge(3600);
        res.addCookie(cookie);

        // Validate on mutations (skip GET, OPTIONS, HEAD, public paths)
        if (isMutating(method) && !isPublicPath(path)) {
            String headerToken = req.getHeader(HEADER_NAME);
            String cookieToken = getTokenFromCookie(req);
            if (cookieToken != null && (headerToken == null || !headerToken.equals(cookieToken))) {
                res.setStatus(403);
                res.setContentType("application/json");
                res.getWriter().write("{\"detail\":\"XSRF token missing or mismatch\"}");
                return;
            }
        }

        chain.doFilter(request, response);
    }

    private String getTokenFromCookie(HttpServletRequest req) {
        if (req.getCookies() == null) return null;
        for (Cookie c : req.getCookies()) {
            if (COOKIE_NAME.equals(c.getName())) return c.getValue();
        }
        return null;
    }

    private boolean isMutating(String method) {
        return "POST".equals(method) || "PUT".equals(method)
                || "PATCH".equals(method) || "DELETE".equals(method);
    }

    private boolean isPublicPath(String path) {
        return path.startsWith("/api/v1/auth/") || path.startsWith("/api/v1/platform/auth/")
                || path.startsWith("/api/v1/public/") || path.startsWith("/swagger")
                || path.startsWith("/v3/api-docs") || path.startsWith("/oauth2");
    }
}
