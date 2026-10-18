package com.sakarrobotics.cloud.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.sakarrobotics.cloud.iam.Role;
import com.sakarrobotics.cloud.iam.RoleName;
import com.sakarrobotics.cloud.iam.RoleRepository;
import com.sakarrobotics.cloud.iam.User;
import com.sakarrobotics.cloud.iam.UserRepository;
import com.sakarrobotics.cloud.iam.UserStatus;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

/**
 * Bootstrap SUPER_ADMIN seeding (local-dev-only configured password support).
 * Uses a real {@link BCryptPasswordEncoder} (not mocked) so the "stored as a
 * genuine BCrypt hash, never plaintext" assertions are real, not assumed —
 * mirroring the same real-encoder-in-tests convention already used for the
 * production {@code AuthService} password check.
 */
@ExtendWith(MockitoExtension.class)
class DevAdminSeederTest {

    private static final String CONFIGURED_PASSWORD = "Admin@1234";

    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private ListAppender<ILoggingEvent> logAppender;

    private DevAdminSeeder seeder(BootstrapProperties properties) {
        return new DevAdminSeeder(properties, userRepository, roleRepository, passwordEncoder);
    }

    private BootstrapProperties propertiesWith(boolean enabled, String email, String password) {
        BootstrapProperties properties = new BootstrapProperties();
        properties.setAdminEnabled(enabled);
        if (email != null) {
            properties.setAdminEmail(email);
        }
        properties.setAdminPassword(password);
        return properties;
    }

    private Role superAdminRole() {
        Role role = new Role(RoleName.SUPER_ADMIN, "Super Admin");
        role.setId(UUID.randomUUID());
        return role;
    }

    @BeforeEach
    void captureLogs() {
        Logger logger = (Logger) org.slf4j.LoggerFactory.getLogger(DevAdminSeeder.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        logger.addAppender(logAppender);
    }

    @AfterEach
    void detachLogAppender() {
        Logger logger = (Logger) org.slf4j.LoggerFactory.getLogger(DevAdminSeeder.class);
        logger.detachAppender(logAppender);
    }

    private List<String> logMessages() {
        return logAppender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    @Test
    void disabled_doesNothing() {
        seeder(propertiesWith(false, null, CONFIGURED_PASSWORD)).run(null);

        verify(userRepository, never()).findByEmailIgnoreCase(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void defaultAdminEmail_isTheNewSakarroboticsComAddress_notTheOldLocalOne() {
        BootstrapProperties properties = new BootstrapProperties();

        assertThat(properties.getAdminEmail()).isEqualTo("admin@sakarrobotics.com");
        assertThat(properties.getAdminEmail()).isNotEqualTo("admin@sakarrobotics.local");
    }

    @Test
    void noExistingUser_configuredPassword_createsAdminWithABCryptHash_ofExactlyThatPassword() {
        BootstrapProperties properties = propertiesWith(true, "admin@sakarrobotics.com", CONFIGURED_PASSWORD);
        when(userRepository.findByEmailIgnoreCase("admin@sakarrobotics.com")).thenReturn(Optional.empty());
        when(roleRepository.findByName(RoleName.SUPER_ADMIN)).thenReturn(Optional.of(superAdminRole()));

        seeder(properties).run(null);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();

        assertThat(saved.getEmail()).isEqualTo("admin@sakarrobotics.com");
        assertThat(saved.getPasswordHash()).isNotEqualTo(CONFIGURED_PASSWORD);
        assertThat(passwordEncoder.matches(CONFIGURED_PASSWORD, saved.getPasswordHash())).isTrue();
        assertThat(saved.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(saved.getRole().getName()).isEqualTo(RoleName.SUPER_ADMIN);
    }

    @Test
    void noExistingUser_configuredPassword_neverLogsThePlaintextPassword() {
        BootstrapProperties properties = propertiesWith(true, "admin@sakarrobotics.com", CONFIGURED_PASSWORD);
        when(userRepository.findByEmailIgnoreCase("admin@sakarrobotics.com")).thenReturn(Optional.empty());
        when(roleRepository.findByName(RoleName.SUPER_ADMIN)).thenReturn(Optional.of(superAdminRole()));

        seeder(properties).run(null);

        assertThat(logMessages()).noneMatch(message -> message.contains(CONFIGURED_PASSWORD));
        assertThat(logMessages()).anyMatch(message -> message.contains("Bootstrap SUPER_ADMIN created"));
    }

    @Test
    void noExistingUser_noConfiguredPassword_fallsBackToARandomPassword_loggedExactlyOnce() {
        BootstrapProperties properties = propertiesWith(true, "admin@sakarrobotics.com", null);
        when(userRepository.findByEmailIgnoreCase("admin@sakarrobotics.com")).thenReturn(Optional.empty());
        when(roleRepository.findByName(RoleName.SUPER_ADMIN)).thenReturn(Optional.of(superAdminRole()));

        seeder(properties).run(null);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();

        // The only place the random password is ever recorded is this one log line —
        // confirm exactly one such line exists (never zero, never duplicated).
        long createdLogLines = logMessages().stream().filter(m -> m.contains("Bootstrap SUPER_ADMIN created")).count();
        assertThat(createdLogLines).isEqualTo(1);
        assertThat(saved.getPasswordHash()).isNotBlank();
    }

    @Test
    void existingUser_noConfiguredPassword_isLeftCompletelyUntouched() {
        BootstrapProperties properties = propertiesWith(true, "admin@sakarrobotics.com", null);
        User existing = new User();
        existing.setEmail("admin@sakarrobotics.com");
        existing.setPasswordHash("existing-hash");
        when(userRepository.findByEmailIgnoreCase("admin@sakarrobotics.com")).thenReturn(Optional.of(existing));

        seeder(properties).run(null);

        verify(userRepository, never()).save(any());
        verify(roleRepository, never()).findByName(any());
    }

    @Test
    void existingUser_configuredPassword_updatesOnlyThePasswordHash_neverOrganizationRoleOrStatus() {
        BootstrapProperties properties = propertiesWith(true, "admin@sakarrobotics.com", CONFIGURED_PASSWORD);
        Role existingRole = superAdminRole();
        UUID existingOrgId = UUID.randomUUID();
        User existing = new User();
        existing.setEmail("admin@sakarrobotics.com");
        existing.setPasswordHash("old-unrelated-hash");
        existing.setOrganizationId(existingOrgId);
        existing.setRole(existingRole);
        existing.setStatus(UserStatus.ACTIVE);
        existing.setFailedLoginAttempts(4);
        existing.setLockedUntil(Instant.now().plusSeconds(600));
        when(userRepository.findByEmailIgnoreCase("admin@sakarrobotics.com")).thenReturn(Optional.of(existing));

        seeder(properties).run(null);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();

        assertThat(passwordEncoder.matches(CONFIGURED_PASSWORD, saved.getPasswordHash())).isTrue();
        assertThat(saved.getPasswordHash()).isNotEqualTo("old-unrelated-hash");
        // Everything else is untouched, except the lockout reset a normal successful login
        // would also perform — never a duplicate user, never a role/org/status change.
        assertThat(saved.getOrganizationId()).isEqualTo(existingOrgId);
        assertThat(saved.getRole()).isEqualTo(existingRole);
        assertThat(saved.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(saved.getFailedLoginAttempts()).isZero();
        assertThat(saved.getLockedUntil()).isNull();
        verify(roleRepository, never()).findByName(any());
    }

    @Test
    void existingUser_configuredPassword_neverLogsThePlaintextPassword() {
        BootstrapProperties properties = propertiesWith(true, "admin@sakarrobotics.com", CONFIGURED_PASSWORD);
        User existing = new User();
        existing.setEmail("admin@sakarrobotics.com");
        existing.setPasswordHash("old-unrelated-hash");
        when(userRepository.findByEmailIgnoreCase("admin@sakarrobotics.com")).thenReturn(Optional.of(existing));

        seeder(properties).run(null);

        assertThat(logMessages()).noneMatch(message -> message.contains(CONFIGURED_PASSWORD));
    }

    @Test
    void blankConfiguredPassword_isTreatedIdenticallyToNoConfiguredPassword() {
        BootstrapProperties properties = propertiesWith(true, "admin@sakarrobotics.com", "   ");
        User existing = new User();
        existing.setEmail("admin@sakarrobotics.com");
        existing.setPasswordHash("existing-hash");
        when(userRepository.findByEmailIgnoreCase("admin@sakarrobotics.com")).thenReturn(Optional.of(existing));

        seeder(properties).run(null);

        verify(userRepository, never()).save(any());
    }
}
