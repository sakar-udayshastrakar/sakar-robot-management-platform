package com.sakarrobotics.cloud.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.sakarrobotics.cloud.common.error.ApiException;
import com.sakarrobotics.cloud.common.error.SakarErrorCode;
import com.sakarrobotics.cloud.iam.PermissionCode;
import com.sakarrobotics.cloud.iam.RoleName;
import com.sakarrobotics.cloud.security.UserPrincipal;

class JwtServiceTest {

    private JwtProperties propertiesWithTtl(long ttlSeconds) {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("unit-test-only-secret-value-0123456789-abcdef");
        properties.setIssuer("sakar-cloud-backend-tests");
        properties.setAccessTokenTtlSeconds(ttlSeconds);
        return properties;
    }

    @Test
    void generateAndParse_roundTripsAllClaims() {
        JwtService service = new JwtService(propertiesWithTtl(900));
        UserPrincipal original = new UserPrincipal(UUID.randomUUID(), "user@example.com", UUID.randomUUID(),
                "/root/child/", RoleName.ORG_ADMIN, Set.of(PermissionCode.ROBOT_VIEW, PermissionCode.ROBOT_CONFIGURE));

        String token = service.generateAccessToken(original);
        UserPrincipal parsed = service.parseAccessToken(token);

        assertThat(parsed.getUserId()).isEqualTo(original.getUserId());
        assertThat(parsed.getEmail()).isEqualTo(original.getEmail());
        assertThat(parsed.getOrganizationId()).isEqualTo(original.getOrganizationId());
        assertThat(parsed.getOrganizationPath()).isEqualTo(original.getOrganizationPath());
        assertThat(parsed.getRole()).isEqualTo(original.getRole());
        assertThat(parsed.getPermissions()).containsExactlyInAnyOrder(PermissionCode.ROBOT_VIEW, PermissionCode.ROBOT_CONFIGURE);
    }

    @Test
    void parseAccessToken_expiredToken_throwsTokenExpired() throws InterruptedException {
        JwtService service = new JwtService(propertiesWithTtl(0));
        UserPrincipal principal = new UserPrincipal(UUID.randomUUID(), "user@example.com", null, null,
                RoleName.SUPER_ADMIN, Set.of());
        String token = service.generateAccessToken(principal);
        Thread.sleep(1100); // ensure the 0-second-TTL token's expiry has actually passed

        assertThatThrownBy(() -> service.parseAccessToken(token))
                .isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getErrorCode()).isEqualTo(SakarErrorCode.TOKEN_EXPIRED));
    }

    @Test
    void parseAccessToken_tamperedToken_throwsUnauthenticated() {
        JwtService service = new JwtService(propertiesWithTtl(900));
        UserPrincipal principal = new UserPrincipal(UUID.randomUUID(), "user@example.com", null, null,
                RoleName.VIEWER, Set.of(PermissionCode.ROBOT_VIEW));
        String token = service.generateAccessToken(principal);
        String tampered = token.substring(0, token.length() - 2) + "xx";

        assertThatThrownBy(() -> service.parseAccessToken(tampered))
                .isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getErrorCode()).isEqualTo(SakarErrorCode.UNAUTHENTICATED));
    }
}
