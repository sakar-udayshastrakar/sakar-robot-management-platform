package com.sakarrobotics.cloud.websocket;

import java.security.Principal;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

import com.sakarrobotics.cloud.common.error.ApiException;
import com.sakarrobotics.cloud.common.error.SakarErrorCode;
import com.sakarrobotics.cloud.security.UserPrincipal;
import com.sakarrobotics.cloud.security.access.TenantAccessGuard;
import com.sakarrobotics.cloud.security.jwt.JwtService;

import lombok.RequiredArgsConstructor;

/**
 * Authenticates every STOMP CONNECT (bearer JWT, same token as the REST
 * API) and authorizes every SUBSCRIBE against the organization hierarchy —
 * the WebSocket-transport counterpart of {@code TenantAccessGuard}'s REST
 * enforcement. A destination not matching {@code /topic/organizations/{id}/**}
 * or {@code /user/**} is rejected outright rather than allowed by default.
 */
@Component
@RequiredArgsConstructor
public class WebSocketAuthChannelInterceptor implements ChannelInterceptor {

    private static final Pattern ORG_SCOPED_DESTINATION = Pattern.compile("^/topic/organizations/([0-9a-fA-F-]{36})/.*$");

    private final JwtService jwtService;
    private final TenantAccessGuard tenantAccessGuard;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            String authHeader = accessor.getFirstNativeHeader("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                throw new ApiException(SakarErrorCode.UNAUTHENTICATED, "Missing bearer token on WebSocket CONNECT");
            }
            UserPrincipal principal = jwtService.parseAccessToken(authHeader.substring("Bearer ".length()));
            Principal stompPrincipal = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
            accessor.setUser(stompPrincipal);
        }

        if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            UserPrincipal principal = extractPrincipal(accessor);
            String destination = accessor.getDestination();
            authorizeDestination(principal, destination);
        }

        return message;
    }

    private void authorizeDestination(UserPrincipal principal, String destination) {
        if (destination == null || destination.startsWith("/user/")) {
            return; // user-private queues are always own-scoped by the broker
        }
        Matcher matcher = ORG_SCOPED_DESTINATION.matcher(destination);
        if (!matcher.matches()) {
            throw new ApiException(SakarErrorCode.FORBIDDEN, "Unrecognized or unauthorized subscription destination");
        }
        UUID targetOrgId = UUID.fromString(matcher.group(1));
        tenantAccessGuard.assertOrganizationAccess(principal, targetOrgId);
    }

    private static UserPrincipal extractPrincipal(StompHeaderAccessor accessor) {
        if (accessor.getUser() instanceof UsernamePasswordAuthenticationToken token
                && token.getPrincipal() instanceof UserPrincipal principal) {
            return principal;
        }
        throw new ApiException(SakarErrorCode.UNAUTHENTICATED, "WebSocket session is not authenticated");
    }
}
