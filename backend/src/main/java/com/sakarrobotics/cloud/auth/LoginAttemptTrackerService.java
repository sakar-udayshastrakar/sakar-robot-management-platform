package com.sakarrobotics.cloud.auth;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.sakarrobotics.cloud.iam.User;
import com.sakarrobotics.cloud.iam.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * Persists a failed-login bookkeeping update in its OWN transaction
 * (REQUIRES_NEW), separate from {@link AuthService#login}, which throws
 * after calling this. Without this separation, Spring's default rollback-
 * on-unchecked-exception behavior would undo the {@code failedLoginAttempts}
 * increment and lockout together with the exception that reports the
 * failure — silently defeating Part 19.A's account-lockout requirement.
 */
@Service
@RequiredArgsConstructor
public class LoginAttemptTrackerService {

    private static final int LOCKOUT_THRESHOLD = 5;
    private static final long LOCKOUT_DURATION_SECONDS = 900;

    private final UserRepository userRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(java.util.UUID userId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return;
        }
        user.setFailedLoginAttempts(user.getFailedLoginAttempts() + 1);
        if (user.getFailedLoginAttempts() >= LOCKOUT_THRESHOLD) {
            user.setLockedUntil(Instant.now().plusSeconds(LOCKOUT_DURATION_SECONDS));
        }
        userRepository.save(user);
    }
}
