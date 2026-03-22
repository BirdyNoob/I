package com.icentric.Icentric.security;

import com.icentric.Icentric.tenant.TenantContext;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Parses the Bearer JWT, sets SecurityContext and TenantContext.
 * This is the SOLE source of tenant identity for authenticated requests.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String header = request.getHeader("Authorization");

        if (header == null || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(7);

        try {
            Claims claims = jwtService.parse(token);

            String tenant = claims.get("tenant", String.class);
            String role   = claims.get("role", String.class);
            String email  = claims.getSubject();
            Object userIdClaim = claims.get("userId");

            // Sole source of tenant context for authenticated requests
            TenantContext.setTenant(tenant);

            // Store impersonation info as request attribute for audit logging
            String impersonatedBy = claims.get("impersonatedBy", String.class);
            if (impersonatedBy != null) {
                request.setAttribute("impersonatedBy", impersonatedBy);
                Object sessionId = claims.get("impersonationSessionId");
                if (sessionId != null) {
                    request.setAttribute("impersonationSessionId", sessionId.toString());
                }
            }

            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(
                            email,
                            null,
                            List.of(new SimpleGrantedAuthority(role))
                    );

            if (userIdClaim != null) {
                auth.setDetails(userIdClaim.toString());
            }

            SecurityContextHolder.getContext().setAuthentication(auth);

            filterChain.doFilter(request, response);

        } catch (ExpiredJwtException ex) {
            writeUnauthorized(response, "Token expired");
        } catch (JwtException ex) {
            writeUnauthorized(response, "Invalid token");
        } finally {
            TenantContext.clear();
        }
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(
                "{\"type\":\"about:blank\",\"title\":\"" + message + "\",\"status\":401}"
        );
    }
}
