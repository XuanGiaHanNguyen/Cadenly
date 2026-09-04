package com.cadenly.scheduler.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP-over-WebSocket transport. Clients only subscribe (/topic) in
 * Phase 4 - there's no client-to-server app-destination traffic yet, so no
 * "/app" prefix is configured.
 *
 * Auth (Phase 10): SockJS's handshake is a plain HTTP request before it
 * upgrades, so it passes through SecurityConfig's filter chain like any
 * other request - /ws/** requires an authenticated session there, so an
 * anonymous client can't complete the handshake at all. No WebSocket-
 * specific auth mechanism needed. (Extension point if per-user topics are
 * ever needed: a custom DefaultHandshakeHandler#determineUser() can carry
 * the already-authenticated Principal from the handshake request into the
 * WebSocket session - not needed today since /topic/bookings is a single
 * shared broadcast, not scoped per user.)
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
    }
}
