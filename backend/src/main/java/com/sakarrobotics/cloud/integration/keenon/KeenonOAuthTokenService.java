package com.sakarrobotics.cloud.integration.keenon;

import java.time.Duration;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import tools.jackson.databind.JsonNode;
import com.sakarrobotics.cloud.common.error.ApiException;
import com.sakarrobotics.cloud.common.error.SakarErrorCode;

import lombok.RequiredArgsConstructor;

/**
 * Obtains and caches the Keenon Open Platform OAuth {@code client_credentials}
 * access token. The token is cached in Redis (never in application memory
 * shared across requests unencrypted-at-rest-on-disk, never logged) with a
 * TTL derived from the vendor's own {@code expires_in} — never returned to
 * any client (SAKAR_SECURITY_REQUIREMENTS.md §13.A).
 */
@Service
@RequiredArgsConstructor
public class KeenonOAuthTokenService {

    private static final String REDIS_KEY = "sakar:keenon:access_token";
    private static final Duration EXPIRY_SAFETY_MARGIN = Duration.ofSeconds(30);

    private final KeenonProperties properties;
    private final StringRedisTemplate redisTemplate;

    public String currentAccessToken() {
        if (!properties.isEnabled()) {
            throw new ApiException(SakarErrorCode.INTEGRATION_UNAVAILABLE,
                    "Keenon Open Platform integration is disabled in this environment");
        }
        String cached = redisTemplate.opsForValue().get(REDIS_KEY);
        if (cached != null) {
            return cached;
        }
        return fetchAndCacheToken();
    }

    private String fetchAndCacheToken() {
        if (properties.getClientId() == null || properties.getClientSecret() == null
                || properties.getClientId().isBlank() || properties.getClientSecret().isBlank()) {
            throw new ApiException(SakarErrorCode.INTEGRATION_UNAVAILABLE,
                    "Keenon client_id/client_secret are not configured");
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", properties.getClientId());
        form.add("client_secret", properties.getClientSecret());
        form.add("grant_type", "client_credentials");

        JsonNode response;
        try {
            response = RestClient.builder().build()
                    .post()
                    .uri(properties.getBaseUrl() + "/api/open/oauth/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (Exception ex) {
            throw new ApiException(SakarErrorCode.VENDOR_API_ERROR, "Keenon OAuth token request failed", ex);
        }

        if (response == null || !response.hasNonNull("access_token")) {
            throw new ApiException(SakarErrorCode.VENDOR_API_ERROR, "Keenon OAuth response did not contain an access_token");
        }

        String accessToken = response.get("access_token").asText();
        long expiresInSeconds = response.hasNonNull("expires_in") ? response.get("expires_in").asLong() : 3600L;
        Duration ttl = Duration.ofSeconds(Math.max(1, expiresInSeconds)).minus(EXPIRY_SAFETY_MARGIN);
        redisTemplate.opsForValue().set(REDIS_KEY, accessToken, ttl.isNegative() ? Duration.ofSeconds(1) : ttl);
        return accessToken;
    }
}
