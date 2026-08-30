package com.sakarrobotics.cloud.command;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.sakarrobotics.cloud.IntegrationTestSupport;
import com.sakarrobotics.cloud.org.Organization;
import com.sakarrobotics.cloud.org.OrganizationType;

/**
 * Roadmap Phase 6/7 "Robot Agent Command Loop" — {@code sakar.commands.expiry-sweep.enabled}
 * is {@code false} in the test profile (see {@code application-test.yml}),
 * so {@link CommandExpiryService#sweep()} is called directly here, exactly
 * like {@code RobotOfflineWatcherServiceTest} does for its own sweep.
 */
class CommandExpiryServiceTest extends IntegrationTestSupport {

    @Autowired
    private CommandExpiryService commandExpiryService;
    @Autowired
    private RobotCommandRepository robotCommandRepository;
    @Autowired
    private CommandResultRepository commandResultRepository;

    @Test
    void overdueNonTerminalCommand_isMarkedTimedOutWithAResultRow() {
        Organization org = createOrganization("Org " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        RobotCommand command = aCommand(org.getId(), CommandStatus.SENT, Instant.now().minusSeconds(5));

        commandExpiryService.sweep();

        assertThat(robotCommandRepository.findById(command.getId())).hasValueSatisfying(c -> {
            assertThat(c.getStatus()).isEqualTo(CommandStatus.COMMAND_TIMEOUT);
            assertThat(c.getCompletedAt()).isNotNull();
        });
        assertThat(commandResultRepository.findAll()).anyMatch(r -> r.getCommandId().equals(command.getId())
                && r.getResult().equals(CommandStatus.COMMAND_TIMEOUT.name()));
    }

    @Test
    void notYetExpiredCommand_isLeftAlone() {
        Organization org = createOrganization("Org " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        RobotCommand command = aCommand(org.getId(), CommandStatus.SENT, Instant.now().plusSeconds(60));

        commandExpiryService.sweep();

        assertThat(robotCommandRepository.findById(command.getId())).hasValueSatisfying(
                c -> assertThat(c.getStatus()).isEqualTo(CommandStatus.SENT));
    }

    @Test
    void alreadyTerminalCommand_isNeverTouchedEvenIfPastExpiry() {
        Organization org = createOrganization("Org " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        RobotCommand command = aCommand(org.getId(), CommandStatus.COMMAND_SUCCESS, Instant.now().minusSeconds(5));
        command.setCompletedAt(Instant.now().minusSeconds(1));
        robotCommandRepository.save(command);
        // Read back through the DB once before sweeping, so the comparison below is round-trip
        // vs. round-trip (H2/Postgres timestamp columns don't preserve full JVM nanosecond
        // precision) rather than round-trip vs. a raw in-memory Instant.
        Instant persistedCompletedAtBeforeSweep = robotCommandRepository.findById(command.getId()).orElseThrow().getCompletedAt();

        commandExpiryService.sweep();

        assertThat(robotCommandRepository.findById(command.getId())).hasValueSatisfying(c -> {
            assertThat(c.getStatus()).isEqualTo(CommandStatus.COMMAND_SUCCESS);
            assertThat(c.getCompletedAt()).isEqualTo(persistedCompletedAtBeforeSweep);
        });
    }

    private RobotCommand aCommand(UUID organizationId, CommandStatus status, Instant expiresAt) {
        RobotCommand command = new RobotCommand();
        command.setRobotId(UUID.randomUUID());
        command.setIssuedBy(UUID.randomUUID());
        command.setOrganizationId(organizationId);
        command.setCommandType("START_TASK");
        command.setNonce(UUID.randomUUID().toString());
        command.setExpiresAt(expiresAt);
        command.setStatus(status);
        return robotCommandRepository.save(command);
    }
}
