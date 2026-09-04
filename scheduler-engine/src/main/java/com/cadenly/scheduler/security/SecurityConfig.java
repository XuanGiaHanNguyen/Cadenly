package com.cadenly.scheduler.security;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Session-based auth (see Phase 10 design notes for why, over JWT, given
 * this is one monolith with one browser client): a real Spring Security
 * session, BCrypt passwords, CSRF via a readable cookie the dashboard
 * mirrors into a header on state-changing requests.
 *
 * /api/tasks/submit stays permitAll() deliberately - see
 * TaskSubmissionController's class doc for why (Service B's integration
 * contract must not change) and its TODO for the deferred shared-secret
 * hardening. Everything else that exposes task/calendar data, including
 * the WebSocket handshake at /ws/** (a plain HTTP request before it
 * upgrades, so it's gated by this same filter chain, cookie and all),
 * requires an authenticated session.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of("http://localhost:3000"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Content-Type", "X-XSRF-TOKEN"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        // The default XorCsrfTokenRequestAttributeHandler masks the token for BREACH
                        // protection, meant for a server-rendered form re-embedding a fresh masked value
                        // per response. A JS client instead reads the raw cookie once and echoes it back
                        // verbatim - the plain (non-XOR) handler makes .getToken() return that same raw
                        // value everywhere, so what's in the cookie is exactly what's expected in the
                        // header. This is Spring Security's own documented recommendation for SPA clients.
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                        // login/register: no session exists yet to have a CSRF token tied to, and a browser
                        // can't be tricked into damaging one that doesn't exist yet either.
                        // /api/tasks/submit: server-to-server from Service B (httpx, no cookies, no CSRF
                        // token) - must stay reachable exactly as before this migration, see
                        // TaskSubmissionController's class doc.
                        .ignoringRequestMatchers("/api/auth/login", "/api/auth/register", "/api/tasks/submit"))
                .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/api/tasks/submit").permitAll()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex.authenticationEntryPoint(
                        (request, response, authException) -> response.sendError(HttpServletResponse.SC_UNAUTHORIZED)));
        return http.build();
    }
}
