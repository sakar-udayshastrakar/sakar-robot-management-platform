package com.sakarrobotics.cloud.websocket;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import com.sakarrobotics.cloud.common.error.ApiException;
import com.sakarrobotics.cloud.common.error.SakarErrorCode;
import com.sakarrobotics.cloud.iam.RoleName;
import com.sakarrobotics.cloud.security.UserPrincipal;
import com.sakarrobotics.cloud.security.access.TenantAccessGuard;
import com.sakarrobotics.cloud.security.jwt.JwtService;

/**
 * WebSocket tenant isolation (Phase 3 Security Hardening Part 18/21 —
 * previously untested at the unit level, only indirectly relied upon).
 * Deliberately a plain unit test, not a real WebSocket/STOMP client
 * integration test: {@link WebSocketAuthChannelInterceptor#preSend} is a
 * pure function of its inputs plus its two collaborators, so exercising it
 * directly gives the same assurance without the flakiness of a live
 * socket handshake.
 */
@ExtendWith(MockitoExtension.class)
class WebSocketAuthChannelInterceptorTest {

    @Mock
    private JwtService jwtService;
    @Mock
    private TenantAccessGuard tenantAccessGuard;

    // Built fresh per call, not as a field initializer - @Mock fields are injected by
    // MockitoExtension's TestInstancePostProcessor, which runs AFTER instance field initializers
    // (same reasoning as MqttInboundListenerTest).
    private WebSocketAuthChannelInterceptor interceptor() {
        return new WebSocketAuthChannelInterceptor(jwtService, tenantAccessGuard);
    }

    @Test
    void connect_withoutABearerToken_isRejected() {
        Message<byte[]> connectFrame = frame(StompCommand.CONNECT, null, null);

        assertThatThrownBy(() -> interceptor().preSend(connectFrame, null))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> org.assertj.core.api.Assertions.assertThat(((ApiException) ex).getErrorCode())
                        .isEqualTo(SakarErrorCode.UNAUTHENTICATED));
    }

    @Test
    void subscribe_withoutAPriorConnectAuthentication_isRejected() {
        UUID orgId = UUID.randomUUID();
        Message<byte[]> subscribeFrame = frame(StompCommand.SUBSCRIBE, "/topic/organizations/" + orgId + "/robots/x/telemetry", null);

        assertThatThrownBy(() -> interceptor().preSend(subscribeFrame, null))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> org.assertj.core.api.Assertions.assertThat(((ApiException) ex).getErrorCode())
                        .isEqualTo(SakarErrorCode.UNAUTHENTICATED));
    }

    @Test
    void subscribe_toAnUnrelatedOrganizationsDestination_isDeniedByTheTenantGuard() {
        UUID ownOrgId = UUID.randomUUID();
        UUID unrelatedOrgId = UUID.randomUUID();
        UserPrincipal principal = principal(ownOrgId);
        doThrow(new ApiException(SakarErrorCode.TENANT_ACCESS_DENIED, "denied"))
                .when(tenantAccessGuard).assertOrganizationAccess(eq(principal), eq(unrelatedOrgId));

        Message<byte[]> subscribeFrame = frame(StompCommand.SUBSCRIBE, "/topic/organizations/" + unrelatedOrgId + "/robots/x/telemetry", principal);

        assertThatThrownBy(() -> interceptor().preSend(subscribeFrame, null))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> org.assertj.core.api.Assertions.assertThat(((ApiException) ex).getErrorCode())
                        .isEqualTo(SakarErrorCode.TENANT_ACCESS_DENIED));
        verify(tenantAccessGuard).assertOrganizationAccess(principal, unrelatedOrgId);
    }

    @Test
    void subscribe_toOwnOrganizationsDestination_isAllowed() {
        UUID ownOrgId = UUID.randomUUID();
        UserPrincipal principal = principal(ownOrgId);
        // No stubbed exception - assertOrganizationAccess is a no-op success for the caller's own org.

        Message<byte[]> subscribeFrame = frame(StompCommand.SUBSCRIBE, "/topic/organizations/" + ownOrgId + "/robots/x/telemetry", principal);

        assertThatCode(() -> interceptor().preSend(subscribeFrame, null)).doesNotThrowAnyException();
        verify(tenantAccessGuard).assertOrganizationAccess(principal, ownOrgId);
    }

    @Test
    void subscribe_toAnUnrecognizedDestination_isForbiddenOutright() {
        UserPrincipal principal = principal(UUID.randomUUID());
        Message<byte[]> subscribeFrame = frame(StompCommand.SUBSCRIBE, "/topic/some-other-thing", principal);

        assertThatThrownBy(() -> interceptor().preSend(subscribeFrame, null))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> org.assertj.core.api.Assertions.assertThat(((ApiException) ex).getErrorCode())
                        .isEqualTo(SakarErrorCode.FORBIDDEN));
    }

    @Test
    void subscribe_toOwnUserPrivateQueue_bypassesTheOrganizationCheckEntirely() {
        UserPrincipal principal = principal(UUID.randomUUID());
        Message<byte[]> subscribeFrame = frame(StompCommand.SUBSCRIBE, "/user/queue/notifications", principal);

        assertThatCode(() -> interceptor().preSend(subscribeFrame, null)).doesNotThrowAnyException();
        verify(tenantAccessGuard, org.mockito.Mockito.never()).assertOrganizationAccess(any(), any());
    }

    private static UserPrincipal principal(UUID organizationId) {
        return new UserPrincipal(UUID.randomUUID(), "user@example.com", organizationId, organizationId.toString(), RoleName.VIEWER, Set.of());
    }

    private static Message<byte[]> frame(StompCommand command, String destination, UserPrincipal principal) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        if (destination != null) {
            accessor.setDestination(destination);
        }
        if (principal != null) {
            accessor.setUser(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        }
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
