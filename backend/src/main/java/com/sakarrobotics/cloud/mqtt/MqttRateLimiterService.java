package com.sakarrobotics.cloud.mqtt;

import java.time.Duration;
import java.util.UUID;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

/**
 * Redis-backed, per-robot MQTT ingestion rate limit (Phase 3 Security
 * Hardening — "Rate limiting" was an explicitly flagged gap in the
 * original Phase 3 implementation). Same fixed-window counter mechanism
 * {@code LoginRateLimiterService} already uses for login brute-force
 * protection, applied here to a flooding/misbehaving MQTT publisher
 * instead.
 *
 * <p>Keyed on the envelope's claimed {@code robotId} — deliberately
 * applied <em>before</em> that id is verified against the robot registry,
 * so a flood of messages under a nonexistent/spoofed id is bucketed and
 * bounded on its own key rather than ever reaching a database lookup per
 * message.
 */
@Service
@RequiredArgsConstructor
public class MqttRateLimiterService {

    private final StringRedisTemplate redisTemplate;
    private final MqttProperties properties;

    public boolean isRateLimited(UUID robotId) {
        String key = key(robotId);
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redisTemplate.expire(key, Duration.ofSeconds(properties.getRateLimitWindowSeconds()));
        }
        return count != null && count > properties.getRateLimitMaxMessages();
    }

    private static String key(UUID robotId) {
        return "sakar:mqtt-rate:" + robotId;
    }
}
