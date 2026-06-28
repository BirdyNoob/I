package com.icentric.Icentric.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Rejects API requests without X-Client-Version header.
 * Prevents raw curl/scraper access. Only enforced on /api/ paths.
 */
@Component
@Order(4)
public class ClientVersionFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest req = (HttpServletRequest) request;
        String path = req.getRequestURI();

        // Only enforce on API paths (skip swagger, public assets, OIDC)
        if (path.startsWith("/api/") && !"OPTIONS".equals(req.getMethod())) {
            String clientVersion = req.getHeader("X-Client-Version");
            if (clientVersion == null || clientVersion.isBlank()) {
                HttpServletResponse res = (HttpServletResponse) response;
                res.setStatus(403);
                res.setContentType("application/json");
                res.getWriter().write("{\"detail\":\"X-Client-Version header required\"}");
                return;
            }
        }

        chain.doFilter(request, response);
    }
}
