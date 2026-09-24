package com.sakarrobotics.cloud.bootstrap;

import java.security.SecureRandom;
import java.util.Base64;

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
 * Creates exactly one bootstrap {@code SUPER_ADMIN} account on startup, only
 * when explicitly enabled (never in a deployment that hasn't opted in via
 * {@code SAKAR_BOOTSTRAP_ADMIN=true}) and only if no user exists yet. The
 * generated password is logged to stdout ONCE at startup and never
 * persisted anywhere in plaintext (this class holds no reference to it
 * after the log line) — it exists purely so a freshly-provisioned
 * environment has a way in without a hardcoded credential in source
 * control (Master Requirements Part 26 / SAKAR_SECURITY_REQUIREMENTS.md
 * §13). This is deliberately NOT part of any Flyway migration.
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
        if (userRepository.existsByEmailIgnoreCase(properties.getAdminEmail())) {
            return;
        }

        String generatedPassword = randomPassword();
        User admin = new User();
        admin.setEmail(properties.getAdminEmail());
        admin.setPasswordHash(passwordEncoder.encode(generatedPassword));
        admin.setFullName("Bootstrap Super Admin");
        admin.setRole(roleRepository.findByName(RoleName.SUPER_ADMIN)
                .orElseThrow(() -> new IllegalStateException("SUPER_ADMIN role missing — Flyway migrations did not run")));
        admin.setStatus(UserStatus.ACTIVE);
        admin.setUserType(UserType.INTERNAL); // cross-organization Sakar staff, matches V19's own backfill rule
        userRepository.save(admin);

        log.warn("=== Bootstrap SUPER_ADMIN created: {} / {} — CHANGE THIS PASSWORD IMMEDIATELY, "
                + "this line is the only place it is ever recorded ===", properties.getAdminEmail(), generatedPassword);
    }

    private static String randomPassword() {
        byte[] bytes = new byte[18];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
