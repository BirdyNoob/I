package com.icentric.Icentric.identity.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Called when OIDC login fails (user not provisioned, account deactivated, etc.)
 *
 * <p>Redirects to the frontend login page with an {@code ssoError} query parameter
 * so the UI can display a meaningful message to the user.
 *
 * <p>Error codes map to {@link IcentricOidcUserService}:
 * <ul>
 *   <li>{@code user_not_provisioned} — email not in system.users</li>
 *   <li>{@code account_deactivated}  — user is_active = false</li>
 *   <li>{@code no_workspace_access}  — no rows in tenant_users</li>
 *   <li>{@code sso_failed}           — generic / unexpected failure</li>
 * </ul>
 */
@Slf4j
@Component
public class OidcFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    @Value("${icentric.frontend.url}")
    private String frontendUrl;

    @Override
    public void onAuthenticationFailure(HttpServletRequest request,
                                        HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {

        String errorCode = "sso_failed"; // default

        if (exception instanceof OAuth2AuthenticationException oauthEx) {
            errorCode = oauthEx.getError().getErrorCode();
        }

        log.warn("SSO login failed: errorCode={}, message={}", errorCode, exception.getMessage());

        String redirectUrl = frontendUrl + "/login?ssoError="
                + URLEncoder.encode(errorCode, StandardCharsets.UTF_8);

        response.sendRedirect(redirectUrl);
    }
}
