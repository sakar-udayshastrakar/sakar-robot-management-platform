package com.sakarrobotics.cloud.security.jwt;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Service;

import com.sakarrobotics.cloud.common.error.ApiException;
import com.sakarrobotics.cloud.common.error.SakarErrorCode;
import com.sakarrobotics.cloud.iam.PermissionCode;
import com.sakarrobotics.cloud.iam.RoleName;
import com.sakarrobotics.cloud.security.UserPrincipal;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;

/**
 * Issues and verifies the short-lived JWT access token (Master Requirements
 * Part 19.A). Refresh tokens are deliberately NOT JWTs — see
 * {@code com.sakarrobotics.cloud.auth.RefreshTokenService} — they are opaque,
 * server-tracked values so they can be individually revoked and rotated.
 */
@Service
@RequiredArgsConstructor
public class JwtService {

    private static final String CLAIM_ORG_ID = "org";
    private static final String CLAIM_ORG_PATH = "org_path";
    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_PERMISSIONS = "perms";
    private static final String CLAIM_EMAIL = "email";

    private final JwtProperties properties;

    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(UserPrincipal principal) {
        Instant now = Instant.now();
        var builder = Jwts.builder()
                .subject(principal.getUserId().toString())
                .issuer(properties.getIssuer())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(properties.getAccessTokenTtlSeconds())))
                .claim(CLAIM_EMAIL, principal.getEmail())
                .claim(CLAIM_ROLE, principal.getRole().name())
                .claim(CLAIM_PERMISSIONS, principal.getPermissions().stream().map(Enum::name).toList());
        if (principal.getOrganizationId() != null) {
            builder.claim(CLAIM_ORG_ID, principal.getOrganizationId().toString());
            builder.claim(CLAIM_ORG_PATH, principal.getOrganizationPath());
        }
        return builder.signWith(signingKey()).compact();
    }

    public long accessTokenTtlSeconds() {
        return properties.getAccessTokenTtlSeconds();
    }

    public UserPrincipal parseAccessToken(String token) {
        Claims claims;
        try {
            claims = Jwts.parser()
                    .verifyWith(signingKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException ex) {
            throw new ApiException(SakarErrorCode.TOKEN_EXPIRED, "Access token has expired");
        } catch (JwtException | IllegalArgumentException ex) {
            throw new ApiException(SakarErrorCode.UNAUTHENTICATED, "Invalid access token");
        }

        UUID userId = UUID.fromString(claims.getSubject());
        String email = claims.get(CLAIM_EMAIL, String.class);
        String orgIdRaw = claims.get(CLAIM_ORG_ID, String.class);
        UUID orgId = orgIdRaw != null ? UUID.fromString(orgIdRaw) : null;
        String orgPath = claims.get(CLAIM_ORG_PATH, String.class);
        RoleName role = RoleName.valueOf(claims.get(CLAIM_ROLE, String.class));
        @SuppressWarnings("unchecked")
        List<String> rawPermissions = claims.get(CLAIM_PERMISSIONS, List.class);
        Set<PermissionCode> permissions = rawPermissions == null
                ? Set.of()
                : rawPermissions.stream().map(PermissionCode::valueOf).collect(Collectors.toSet());

        return new UserPrincipal(userId, email, orgId, orgPath, role, permissions);
    }
}
