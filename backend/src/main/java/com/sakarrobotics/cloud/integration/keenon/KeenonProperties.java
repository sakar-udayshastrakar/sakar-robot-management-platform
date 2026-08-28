package com.sakarrobotics.cloud.integration.keenon;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * Keenon Open Platform integration config (Master Requirements Part 10/40).
 * {@code clientId}/{@code clientSecret} are supplied only via environment
 * variable / secrets manager — never hardcoded, never committed
 * (SAKAR_SECURITY_REQUIREMENTS.md §13.A). {@code enabled=false} by default
 * so no outbound call is ever attempted unless a deployment explicitly
 * turns this on with real credentials.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "sakar.integration.keenon")
public class KeenonProperties {

    private boolean enabled = false;

    private String baseUrl = "https://cloud.robotkeenon.com";

    private String clientId;

    private String clientSecret;

    private int connectTimeoutMs = 5000;

    private int readTimeoutMs = 8000;

    /** Shared secret Keenon must present (header) when calling our webhook endpoint. Never logged. */
    private String webhookSecret;
}
