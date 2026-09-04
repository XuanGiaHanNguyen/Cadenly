package com.cadenly.scheduler.web;

import com.cadenly.scheduler.persistence.UserEntity;
import com.cadenly.scheduler.persistence.UserJpaRepository;
import com.cadenly.scheduler.persistence.UserNameAliasEntity;
import com.cadenly.scheduler.persistence.UserNameAliasJpaRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Real accounts: registering here both creates a login-capable user and
 * makes them an assignable task-owner (Phase 10 design decision to unify
 * the two) - it supersedes the earlier name-only "POST /api/owners".
 * JSON login/logout rather than Spring Security's default form login,
 * since the dashboard is a Next.js SPA making fetch calls, not submitting
 * an HTML form.
 */
@RestController
public class AuthController {

    private static final List<String> VALID_CALENDAR_PREFERENCES = List.of("google", "manual");

    public record RegisterRequest(String email, String password, String displayName) {
    }

    public record LoginRequest(String email, String password) {
    }

    public record OnboardingRequest(String occupation, String calendarPreference) {
    }

    /** occupation/calendarPreference are both null until onboarding completes - the frontend treats occupation == null as "needs onboarding". */
    public record CurrentUserResponse(UUID id, String email, String displayName, String occupation, String calendarPreference) {
    }

    private final UserJpaRepository userJpaRepository;
    private final UserNameAliasJpaRepository aliasJpaRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;

    public AuthController(UserJpaRepository userJpaRepository, UserNameAliasJpaRepository aliasJpaRepository,
                           PasswordEncoder passwordEncoder, AuthenticationManager authenticationManager) {
        this.userJpaRepository = userJpaRepository;
        this.aliasJpaRepository = aliasJpaRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
    }

    @PostMapping("/api/auth/register")
    public ResponseEntity<CurrentUserResponse> register(@RequestBody RegisterRequest request, HttpServletRequest httpRequest) {
        if (request.email() == null || request.email().isBlank() || request.password() == null || request.password().isBlank()
                || request.displayName() == null || request.displayName().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        if (userJpaRepository.findByEmail(request.email()).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }

        UserEntity user = new UserEntity(request.email(), request.displayName(), passwordEncoder.encode(request.password()));
        userJpaRepository.save(user);
        aliasJpaRepository.save(new UserNameAliasEntity(user.getId(), request.displayName().strip().toLowerCase()));

        establishSession(request.email(), request.password(), httpRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(user));
    }

    @PostMapping("/api/auth/login")
    public ResponseEntity<CurrentUserResponse> login(@RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        try {
            establishSession(request.email(), request.password(), httpRequest);
        } catch (BadCredentialsException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        UserEntity user = userJpaRepository.findByEmail(request.email()).orElseThrow();
        return ResponseEntity.ok(toResponse(user));
    }

    @PostMapping("/api/auth/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/auth/me")
    public ResponseEntity<CurrentUserResponse> me(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return userJpaRepository.findByEmail(authentication.getName())
                .map(u -> ResponseEntity.ok(toResponse(u)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    @PostMapping("/api/auth/onboarding")
    public ResponseEntity<CurrentUserResponse> completeOnboarding(@RequestBody OnboardingRequest request, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (request.occupation() == null || request.occupation().isBlank()
                || request.calendarPreference() == null || !VALID_CALENDAR_PREFERENCES.contains(request.calendarPreference())) {
            return ResponseEntity.badRequest().build();
        }
        UserEntity user = userJpaRepository.findByEmail(authentication.getName()).orElseThrow();
        user.setOccupation(request.occupation().strip());
        user.setCalendarPreference(request.calendarPreference());
        userJpaRepository.save(user);
        return ResponseEntity.ok(toResponse(user));
    }

    private static CurrentUserResponse toResponse(UserEntity user) {
        return new CurrentUserResponse(user.getId(), user.getEmail(), user.getDisplayName(), user.getOccupation(), user.getCalendarPreference());
    }

    private void establishSession(String email, String password, HttpServletRequest httpRequest) {
        Authentication authResult = authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(email, password));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authResult);
        SecurityContextHolder.setContext(context);
        httpRequest.getSession(true).setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
    }
}
