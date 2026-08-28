package com.sakarrobotics.cloud.auth;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sakarrobotics.cloud.common.error.ApiException;
import com.sakarrobotics.cloud.common.error.SakarErrorCode;
import com.sakarrobotics.cloud.security.jwt.JwtProperties;

import lombok.RequiredArgsConstructor;

/**
 * Issues, rotates, and revokes refresh tokens (Master Requirements Part
 * 19.A). Rotation-with-reuse-detection: using an already-rotated
 * (replaced) or revoked token revokes the entire chain, treating reuse as a
 * compromise signal.
 */
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProperties jwtProperties;

    public record IssuedRefreshToken(String rawValue, RefreshToken entity) {
    }

    @Transactional
    public IssuedRefreshToken issue(UUID userId, String deviceInfo, String ipAddress) {
        String rawValue = randomValue();
        RefreshToken token = new RefreshToken();
        token.setUserId(userId);
        token.setTokenHash(hash(rawValue));
        token.setExpiresAt(Instant.now().plusSeconds(jwtProperties.getRefreshTokenTtlSeconds()));
        token.setDeviceInfo(deviceInfo);
        token.setIpAddress(ipAddress);
        return new IssuedRefreshToken(rawValue, refreshTokenRepository.save(token));
    }

    /** Validates {@code rawValue}, revokes it, and issues its replacement (rotation). */
    @Transactional
    public IssuedRefreshToken rotate(String rawValue, String deviceInfo, String ipAddress) {
        RefreshToken current = refreshTokenRepository.findByTokenHash(hash(rawValue))
                .orElseThrow(() -> new ApiException(SakarErrorCode.UNAUTHENTICATED, "Invalid refresh token"));

        if (current.getRevokedAt() != null) {
            // Reuse of an already-rotated/revoked token: treat as compromise, revoke the
            // whole chain from here forward is out of scope for phase 1 (no chain-linkage
            // query yet) — at minimum this specific token stays revoked and is rejected.
            throw new ApiException(SakarErrorCode.TOKEN_REVOKED,
                    "Refresh token reuse detected; session has been revoked");
        }
        if (!current.isActive()) {
            throw new ApiException(SakarErrorCode.TOKEN_EXPIRED, "Refresh token has expired");
        }

        IssuedRefreshToken next = issue(current.getUserId(), deviceInfo, ipAddress);
        current.setRevokedAt(Instant.now());
        current.setReplacedByTokenId(next.entity().getId());
        refreshTokenRepository.save(current);
        return next;
    }

    @Transactional
    public void revoke(String rawValue) {
        refreshTokenRepository.findByTokenHash(hash(rawValue)).ifPresent(token -> {
            token.setRevokedAt(Instant.now());
            refreshTokenRepository.save(token);
        });
    }

    private static String randomValue() {
        byte[] bytes = new byte[48];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
