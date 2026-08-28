package com.sakarrobotics.cloud.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import lombok.RequiredArgsConstructor;

/**
 * Web/Mobile-facing real-time channel (Master Requirements Part 15/24):
 * live dashboard push, robot state changes, task updates, alerts.
 *
 * <p>Every subscription is authenticated on CONNECT and authorized
 * per-destination on SUBSCRIBE by {@link WebSocketAuthChannelInterceptor} —
 * a user must never be able to subscribe to another organization's robot,
 * site, or event stream (SAKAR_ROBOT_PLATFORM_ARCHITECTURE.md "WebSocket
 * Security"). No controller in this codebase publishes to these
 * destinations yet (that requires the telemetry/event pipeline, Phase 2+)
 * — this is the authenticated, tenant-scoped transport foundation only.
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketAuthChannelInterceptor authChannelInterceptor;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOriginPatterns("http://localhost:*");
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(authChannelInterceptor);
    }
}
