package com.icentric.Icentric.security;

import com.icentric.Icentric.tenant.TenantContext;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.impl.DefaultHeader;
import io.jsonwebtoken.impl.DefaultClaims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock JwtService jwtService;
    @Mock HttpServletRequest request;
    @Mock HttpServletResponse response;
    @Mock FilterChain filterChain;

    JwtAuthenticationFilter filter;

    @BeforeEach
    void setup() {
        filter = new JwtAuthenticationFilter(jwtService);
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @Test
    @DisplayName("no Authorization header → passes through without setting context")
    void noAuthHeader_passesThrough() throws Exception {
        when(request.getHeader("Authorization")).thenReturn(null);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("valid JWT → sets SecurityContext and TenantContext")
    void validJwt_setsContext() throws Exception {
        UUID userId = UUID.randomUUID();
        when(request.getHeader("Authorization")).thenReturn("Bearer valid-jwt");

        DefaultClaims claims = new DefaultClaims(Map.of(
                "sub", "user@acme.com",
                "tenant", "acme",
                "role", "ROLE_LEARNER",
                "userId", userId.toString()
        ));
        when(jwtService.parse("valid-jwt")).thenReturn(claims);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName())
                .isEqualTo("user@acme.com");
    }

    @Test
    @DisplayName("expired JWT → returns 401 JSON response, no filter chain pass")
    void expiredJwt_returns401() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer expired-jwt");
        when(jwtService.parse("expired-jwt"))
                .thenThrow(new ExpiredJwtException(null, null, "Token expired"));

        StringWriter writer = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(writer));

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setStatus(401);
        verify(filterChain, never()).doFilter(any(), any());
        assertThat(writer.toString()).contains("Token expired");
    }

    @Test
    @DisplayName("malformed JWT → returns 401 JSON response")
    void malformedJwt_returns401() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer not.a.jwt");
        when(jwtService.parse("not.a.jwt"))
                .thenThrow(new MalformedJwtException("bad format"));

        StringWriter writer = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(writer));

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setStatus(401);
        verify(filterChain, never()).doFilter(any(), any());
        assertThat(writer.toString()).contains("Invalid token");
    }

    @Test
    @DisplayName("impersonation JWT → stores impersonatedBy in request attribute")
    void impersonationJwt_setsAttribute() throws Exception {
        UUID adminId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        when(request.getHeader("Authorization")).thenReturn("Bearer imp-jwt");

        DefaultClaims claims = new DefaultClaims(Map.of(
                "sub", "admin@sys.com",
                "tenant", "acme",
                "role", "ROLE_ADMIN",
                "userId", UUID.randomUUID().toString(),
                "impersonatedBy", adminId.toString(),
                "impersonationSessionId", sessionId.toString()
        ));
        when(jwtService.parse("imp-jwt")).thenReturn(claims);

        filter.doFilterInternal(request, response, filterChain);

        verify(request).setAttribute("impersonatedBy", adminId.toString());
        verify(request).setAttribute("impersonationSessionId", sessionId.toString());
    }
}
