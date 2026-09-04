package com.cadenly.scheduler.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Spring Security 6's CSRF token is resolved lazily by default (a BREACH
 * mitigation): CookieCsrfTokenRepository only actually writes the
 * XSRF-TOKEN cookie if something calls CsrfToken.getToken() during the
 * request. A view-rendering app gets that for free from its templates;
 * this is a pure JSON API with no view layer, so without this filter the
 * cookie is never written and the dashboard has no way to ever obtain a
 * token to send back - every state-changing authenticated request would
 * permanently 403. This is Spring's own documented fix for SPA clients:
 * force resolution on every request so the cookie always gets set.
 */
public class CsrfCookieFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (csrfToken != null) {
            csrfToken.getToken(); // forces CookieCsrfTokenRepository to actually write the cookie
        }
        filterChain.doFilter(request, response);
    }
}
