package com.sakarrobotics.cloud.auth;

import java.time.Duration;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.sakarrobotics.cloud.common.error.ApiException;
import com.sakarrobotics.cloud.common.error.SakarErrorCode;

import lombok.RequiredArgsConstructor;

/**
 * Redis-backed brute-force protection at the login endpoint, independent of
 * any general API rate limit (Master Requirements Part 19.A). Keyed on
 * email+IP so a distributed attempt against many accounts from one source,
 * or many sources against one account, is throttled either way.
 */
@Service
@RequiredArgsConstructor
public class LoginRateLimiterService {

    // Deliberately higher than AuthService's per-account lockout threshold (5): this is the
    // broader, coarser safety net (protects against spraying many accounts from one email+IP
    // key space), not the primary single-account defense — ACCOUNT_LOCKED should be what a
    // normal single-account brute force actually hits first.
    private static final int MAX_ATTEMPTS = 10;
    private static final Duration WINDOW = Duration.ofMinutes(15);

    private final StringRedisTemplate redisTemplate;

    public void assertNotRateLimited(String email, String ip) {
        String key = key(email, ip);
        String value = redisTemplate.opsForValue().get(key);
        int attempts = value != null ? Integer.parseInt(value) : 0;
        if (attempts >= MAX_ATTEMPTS) {
            throw new ApiException(SakarErrorCode.RATE_LIMITED,
                    "Too many failed login attempts; try again later");
        }
    }

    public void recordFailure(String email, String ip) {
        String key = key(email, ip);
        Long attempts = redisTemplate.opsForValue().increment(key);
        if (attempts != null && attempts == 1L) {
            redisTemplate.expire(key, WINDOW);
        }
    }

    public void recordSuccess(String email, String ip) {
        redisTemplate.delete(key(email, ip));
    }

    private static String key(String email, String ip) {
        return "sakar:login-attempts:" + email.toLowerCase() + ":" + ip;
    }
}
