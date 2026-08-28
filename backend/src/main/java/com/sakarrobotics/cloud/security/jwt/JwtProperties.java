package com.sakarrobotics.cloud.security.jwt;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ConfigurationProperties(prefix = "sakar.security.jwt")
public class JwtProperties {

    /**
     * NEVER a real production secret in source (Part 26 / Section 13). Overridden
     * per-environment via the {@code SAKAR_JWT_SECRET} environment variable /
     * secret manager entry.
     */
    private String secret;

    private String issuer = "sakar-cloud-backend";

    private long accessTokenTtlSeconds = 900;

    private long refreshTokenTtlSeconds = 1_209_600;
}
