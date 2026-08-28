package com.sakarrobotics.cloud.auth;

import java.time.Instant;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sakarrobotics.cloud.audit.AuditService;
import com.sakarrobotics.cloud.auth.dto.TokenResponse;
import com.sakarrobotics.cloud.common.error.ApiException;
import com.sakarrobotics.cloud.common.error.SakarErrorCode;
import com.sakarrobotics.cloud.iam.User;
import com.sakarrobotics.cloud.iam.UserRepository;
import com.sakarrobotics.cloud.iam.UserStatus;
import com.sakarrobotics.cloud.security.PrincipalFactory;
import com.sakarrobotics.cloud.security.UserPrincipal;
import com.sakarrobotics.cloud.security.jwt.JwtService;

import lombok.RequiredArgsConstructor;

/**
 * Authentication foundation (Master Requirements Part 19.A). Login is
 * performed by direct BCrypt comparison rather than Spring Security's
 * {@code AuthenticationManager} — see the note in {@code SecurityConfig}.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PrincipalFactory principalFactory;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final LoginRateLimiterService rateLimiterService;
    private final LoginAttemptTrackerService loginAttemptTrackerService;
    private final AuditService auditService;

    @Transactional
    public TokenResponse login(String email, String rawPassword, String ip, String deviceInfo) {
        rateLimiterService.assertNotRateLimited(email, ip);

        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseGet(() -> {
                    rateLimiterService.recordFailure(email, ip);
                    throw unauthorized(email, ip);
                });

        if (user.getStatus() == UserStatus.SUSPENDED) {
            throw new ApiException(SakarErrorCode.ACCOUNT_SUSPENDED, "This account has been suspended");
        }
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(Instant.now())) {
            throw new ApiException(SakarErrorCode.ACCOUNT_LOCKED,
                    "This account is temporarily locked due to repeated failed login attempts");
        }

        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            registerFailedAttempt(user, email, ip);
            throw unauthorized(email, ip);
        }

        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);
        rateLimiterService.recordSuccess(email, ip);

        UserPrincipal principal = principalFactory.from(user);
        String accessToken = jwtService.generateAccessToken(principal);
        RefreshTokenService.IssuedRefreshToken refreshToken = refreshTokenService.issue(user.getId(), deviceInfo, ip);

        auditService.record(principal, user.getOrganizationId(), null, "LOGIN", "SUCCESS", null, ip, deviceInfo);
        return TokenResponse.of(accessToken, refreshToken.rawValue(), jwtService.accessTokenTtlSeconds());
    }

    @Transactional
    public TokenResponse refresh(String rawRefreshToken, String ip, String deviceInfo) {
        RefreshTokenService.IssuedRefreshToken rotated = refreshTokenService.rotate(rawRefreshToken, deviceInfo, ip);
        User user = userRepository.findById(rotated.entity().getUserId())
                .orElseThrow(() -> new ApiException(SakarErrorCode.USER_NOT_FOUND, "User no longer exists"));
        UserPrincipal principal = principalFactory.from(user);
        String accessToken = jwtService.generateAccessToken(principal);
        return TokenResponse.of(accessToken, rotated.rawValue(), jwtService.accessTokenTtlSeconds());
    }

    public void logout(String rawRefreshToken, UserPrincipal actor) {
        refreshTokenService.revoke(rawRefreshToken);
        auditService.record(actor, actor != null ? actor.getOrganizationId() : null, null, "LOGOUT", "SUCCESS", null, null, null);
    }

    /**
     * Delegates the actual bookkeeping to {@link LoginAttemptTrackerService},
     * which commits in its own transaction — {@link #login} itself always
     * throws right after this, and Spring would otherwise roll the
     * increment/lockout back along with that exception.
     */
    private void registerFailedAttempt(User user, String email, String ip) {
        rateLimiterService.recordFailure(email, ip);
        loginAttemptTrackerService.recordFailure(user.getId());
    }

    private ApiException unauthorized(String email, String ip) {
        return new ApiException(SakarErrorCode.INVALID_CREDENTIALS, "Invalid email or password");
    }
}
