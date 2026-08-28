package com.sakarrobotics.cloud.auth;

import java.time.Instant;
import java.util.UUID;

import com.sakarrobotics.cloud.common.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Server-tracked refresh token (implementation-necessary addition to
 * support Part 19.A's "refresh token rotation" / "token revocation"
 * requirements — a pure-JWT refresh token cannot be individually revoked).
 * Only a SHA-256 hash of the opaque token value is ever persisted, never
 * the raw value.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "replaced_by_token_id")
    private UUID replacedByTokenId;

    @Column(name = "device_info")
    private String deviceInfo;

    @Column(name = "ip_address")
    private String ipAddress;

    public boolean isActive() {
        return revokedAt == null && expiresAt.isAfter(Instant.now());
    }
}
