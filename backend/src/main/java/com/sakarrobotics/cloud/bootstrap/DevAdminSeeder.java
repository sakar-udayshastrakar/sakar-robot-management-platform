package com.sakarrobotics.cloud.bootstrap;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.sakarrobotics.cloud.iam.RoleName;
import com.sakarrobotics.cloud.iam.RoleRepository;
import com.sakarrobotics.cloud.iam.User;
import com.sakarrobotics.cloud.iam.UserRepository;
import com.sakarrobotics.cloud.iam.UserStatus;
import com.sakarrobotics.cloud.iam.UserType;

import lombok.RequiredArgsConstructor;

/**
 * Creates or updates exactly one bootstrap {@code SUPER_ADMIN} account on
 * startup, only when explicitly enabled (never in a deployment that hasn't
 * opted in via {@code SAKAR_BOOTSTRAP_ADMIN=true}).
 *
 * By default no password is configured, so a freshly-created account gets a
 * one-time random password, logged to stdout ONCE and never persisted
 * anywhere in plaintext (this class holds no reference to it after the log
 * line) — this exists so a freshly-provisioned environment has a way in
 * without a hardcoded credential in source control (Master Requirements
 * Part 26 / SAKAR_SECURITY_REQUIREMENTS.md §13). A local developer may
 * instead export {@code SAKAR_BOOTSTRAP_ADMIN_PASSWORD} in their own
 * untracked shell/env-file (never in {@code application.yml},
 * {@code docker-compose.yml}, or any other committed config) to get a known
 * password for local testing; that value is always BCrypt-hashed before
 * persistence and is never logged, whether the account is newly created or
 * already exists. When the account already exists and no password is
 * configured, it is left completely untouched (email/role/org/status and
 * every other field). When it already exists AND a password is configured,
 * only its password hash and the lockout counters a normal successful login
 * would also reset are updated — role/org/status are never touched. This is
 * deliberately NOT part of any Flyway migration.
 */
@Component
@EnableConfigurationProperties(BootstrapProperties.class)
@RequiredArgsConstructor
public class DevAdminSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevAdminSeeder.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final BootstrapProperties properties;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isAdminEnabled()) {
            return;
        }

        String configuredPassword = properties.getAdminPassword();
        boolean hasConfiguredPassword = configuredPassword != null && !configuredPassword.isBlank();

        Optional<User> existing = userRepository.findByEmailIgnoreCase(properties.getAdminEmail());
        if (existing.isPresent()) {
            if (!hasConfiguredPassword) {
                return;
            }
            User user = existing.get();
            user.setPasswordHash(passwordEncoder.encode(configuredPassword));
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(null);
            userRepository.save(user);
            log.warn("=== Bootstrap SUPER_ADMIN password updated for {} from SAKAR_BOOTSTRAP_ADMIN_PASSWORD ===",
                    properties.getAdminEmail());
            return;
        }

        String passwordToUse = hasConfiguredPassword ? configuredPassword : randomPassword();
        User admin = new User();
        admin.setEmail(properties.getAdminEmail());
        admin.setPasswordHash(passwordEncoder.encode(passwordToUse));
        admin.setFullName("Bootstrap Super Admin");
        admin.setRole(roleRepository.findByName(RoleName.SUPER_ADMIN)
                .orElseThrow(() -> new IllegalStateException("SUPER_ADMIN role missing — Flyway migrations did not run")));
        admin.setStatus(UserStatus.ACTIVE);
        admin.setUserType(UserType.INTERNAL); // cross-organization Sakar staff, matches V19's own backfill rule
        userRepository.save(admin);

        if (hasConfiguredPassword) {
            log.warn("=== Bootstrap SUPER_ADMIN created: {} (password from SAKAR_BOOTSTRAP_ADMIN_PASSWORD) ===",
                    properties.getAdminEmail());
        } else {
            log.warn("=== Bootstrap SUPER_ADMIN created: {} / {} — CHANGE THIS PASSWORD IMMEDIATELY, "
                    + "this line is the only place it is ever recorded ===", properties.getAdminEmail(), passwordToUse);
        }
    }

    private static String randomPassword() {
        byte[] bytes = new byte[18];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
